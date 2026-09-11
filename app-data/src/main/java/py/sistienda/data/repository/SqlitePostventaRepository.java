package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.DevolucionResultado;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.VentaPostventa;
import py.sistienda.core.model.VentaPostventaLinea;
import py.sistienda.core.repository.PostventaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SqlitePostventaRepository implements PostventaRepository {
    private static final double EPSILON = 0.000001d;
    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SqliteConnectionFactory connectionFactory;

    public SqlitePostventaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public Optional<VentaPostventa> findVenta(long ventaId) {
        try (Connection connection = connectionFactory.open()) {
            return findVenta(connection, ventaId);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar la venta para postventa.", e);
        }
    }

    @Override
    public void anular(long ventaId, long usuarioId, String motivo) {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                VentaPostventa venta = findVenta(connection, ventaId)
                        .orElseThrow(() -> new ValidationException("No se encontró la venta seleccionada."));
                if (venta.anulada()) {
                    throw new ValidationException("La venta ya está anulada.");
                }
                if (venta.tieneDevoluciones()) {
                    throw new ValidationException("La venta ya tiene devoluciones y no puede anularse completa.");
                }
                exigirCajaOriginalAbierta(connection, venta.cajaSesionId());

                try (var update = connection.prepareStatement(
                        "UPDATE venta SET anulada = 1, motivo_anulacion = ? WHERE id = ? AND anulada = 0")) {
                    update.setString(1, motivo);
                    update.setLong(2, ventaId);
                    if (update.executeUpdate() != 1) {
                        throw new ValidationException("La venta cambió de estado. Actualizá el reporte e intentá nuevamente.");
                    }
                }

                try (var audit = connection.prepareStatement(
                        "INSERT INTO venta_anulacion (venta_id, usuario_id, motivo) VALUES (?, ?, ?)")) {
                    audit.setLong(1, ventaId);
                    audit.setLong(2, usuarioId);
                    audit.setString(3, motivo);
                    audit.executeUpdate();
                }

                for (VentaPostventaLinea linea : venta.lineas()) {
                    insertStockMovement(
                            connection,
                            linea.productoId(),
                            linea.cantidadVendida(),
                            "ANULACION",
                            "ANULACION-TICKET-" + venta.nroTicket(),
                            usuarioId,
                            "Anulación de venta #" + venta.nroTicket() + " · " + motivo
                    );
                }

                connection.commit();
            } catch (Exception e) {
                rollback(connection, e);
                if (e instanceof ValidationException validationException) {
                    throw validationException;
                }
                throw new RuntimeException("No se pudo anular la venta.", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo abrir la transacción de anulación.", e);
        }
    }

    @Override
    public DevolucionResultado devolver(
            long ventaId,
            long cajaSesionId,
            long usuarioId,
            String motivo,
            List<DevolucionLineaSolicitud> solicitudes
    ) {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                exigirCajaDevolucionAbierta(connection, cajaSesionId, usuarioId);
                VentaPostventa venta = findVenta(connection, ventaId)
                        .orElseThrow(() -> new ValidationException("No se encontró la venta seleccionada."));
                if (venta.anulada()) {
                    throw new ValidationException("No se puede devolver una venta anulada.");
                }

                Map<Long, VentaPostventaLinea> disponibles = new HashMap<>();
                for (VentaPostventaLinea linea : venta.lineas()) {
                    disponibles.put(linea.ventaDetalleId(), linea);
                }

                double total = 0d;
                double costo = 0d;
                double ganancia = 0d;
                List<LineaCalculada> calculadas = new ArrayList<>();
                for (DevolucionLineaSolicitud solicitud : solicitudes) {
                    VentaPostventaLinea linea = disponibles.get(solicitud.ventaDetalleId());
                    if (linea == null) {
                        throw new ValidationException("Uno de los productos ya no está disponible para devolver.");
                    }
                    double cantidad = solicitud.cantidad();
                    if (!Double.isFinite(cantidad) || cantidad <= 0
                            || cantidad - linea.cantidadDisponible() > EPSILON) {
                        throw new ValidationException("La cantidad a devolver de “" + linea.producto() + "” ya no es válida.");
                    }
                    double subtotal = cantidad * linea.precioUnitario();
                    double costoLinea = cantidad * linea.costoUnitario();
                    double gananciaLinea = subtotal - costoLinea;
                    total += subtotal;
                    costo += costoLinea;
                    ganancia += gananciaLinea;
                    calculadas.add(new LineaCalculada(linea, cantidad, subtotal, gananciaLinea));
                }
                if (total <= EPSILON) {
                    throw new ValidationException("La devolución debe tener un importe mayor a cero.");
                }

                long devolucionId = insertDevolucion(
                        connection, ventaId, cajaSesionId, usuarioId, venta.metodoPago(),
                        total, costo, ganancia, motivo
                );

                for (LineaCalculada calculada : calculadas) {
                    insertDevolucionDetalle(connection, devolucionId, calculada);
                    insertStockMovement(
                            connection,
                            calculada.linea().productoId(),
                            calculada.cantidad(),
                            "DEVOLUCION",
                            "DEVOLUCION-" + devolucionId,
                            usuarioId,
                            "Devolución ticket #" + venta.nroTicket() + " · " + motivo
                    );
                }

                connection.commit();
                return new DevolucionResultado(
                        devolucionId,
                        ventaId,
                        venta.nroTicket(),
                        total,
                        costo,
                        ganancia
                );
            } catch (Exception e) {
                rollback(connection, e);
                if (e instanceof ValidationException validationException) {
                    throw validationException;
                }
                throw new RuntimeException("No se pudo registrar la devolución.", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo abrir la transacción de devolución.", e);
        }
    }

    private Optional<VentaPostventa> findVenta(Connection connection, long ventaId) throws SQLException {
        String sql = """
                SELECT v.id, v.nro_ticket, v.caja_sesion_id, v.fecha, v.metodo_pago,
                       v.total, v.ganancia_total, v.anulada, v.motivo_anulacion,
                       COALESCE((SELECT SUM(dv.total) FROM devolucion dv WHERE dv.venta_id = v.id), 0) AS total_devuelto
                FROM venta v
                WHERE v.id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ventaId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                List<VentaPostventaLinea> lineas = findLineas(connection, ventaId);
                return Optional.of(new VentaPostventa(
                        result.getLong("id"),
                        result.getLong("nro_ticket"),
                        result.getLong("caja_sesion_id"),
                        parseDate(result.getString("fecha")),
                        MetodoPago.valueOf(result.getString("metodo_pago")),
                        result.getDouble("total"),
                        result.getDouble("ganancia_total"),
                        result.getInt("anulada") != 0,
                        result.getString("motivo_anulacion"),
                        result.getDouble("total_devuelto"),
                        lineas
                ));
            }
        }
    }

    private List<VentaPostventaLinea> findLineas(Connection connection, long ventaId) throws SQLException {
        String sql = """
                SELECT d.id AS detalle_id, d.producto_id, p.nombre, p.unidad_medida,
                       d.cantidad, d.precio_unitario, d.costo_unitario,
                       COALESCE((
                           SELECT SUM(dd.cantidad)
                           FROM devolucion_detalle dd
                           WHERE dd.venta_detalle_id = d.id
                       ), 0) AS cantidad_devuelta
                FROM venta_detalle d
                JOIN producto p ON p.id = d.producto_id
                WHERE d.venta_id = ?
                ORDER BY d.id
                """;
        List<VentaPostventaLinea> lineas = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ventaId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    lineas.add(new VentaPostventaLinea(
                            result.getLong("detalle_id"),
                            result.getLong("producto_id"),
                            result.getString("nombre"),
                            UnidadMedida.valueOf(result.getString("unidad_medida")),
                            result.getDouble("cantidad"),
                            result.getDouble("cantidad_devuelta"),
                            result.getDouble("precio_unitario"),
                            result.getDouble("costo_unitario")
                    ));
                }
            }
        }
        return List.copyOf(lineas);
    }

    private void exigirCajaOriginalAbierta(Connection connection, long cajaSesionId) throws SQLException {
        String sql = "SELECT estado FROM caja_sesion WHERE id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !"ABIERTA".equals(result.getString("estado"))) {
                    throw new ValidationException(
                            "La caja original ya está cerrada. Para mantener el arqueo histórico, registrá una devolución."
                    );
                }
            }
        }
    }

    private void exigirCajaDevolucionAbierta(Connection connection, long cajaSesionId, long usuarioId) throws SQLException {
        String sql = "SELECT 1 FROM caja_sesion WHERE id = ? AND usuario_id = ? AND estado = 'ABIERTA'";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, usuarioId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new ValidationException("La caja ya no está abierta para registrar la devolución.");
                }
            }
        }
    }

    private long insertDevolucion(
            Connection connection,
            long ventaId,
            long cajaSesionId,
            long usuarioId,
            MetodoPago metodoPago,
            double total,
            double costo,
            double ganancia,
            String motivo
    ) throws SQLException {
        String sql = """
                INSERT INTO devolucion (
                    venta_id, caja_sesion_id, usuario_id, metodo_pago,
                    total, costo_total, ganancia_revertida, motivo
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, ventaId);
            statement.setLong(2, cajaSesionId);
            statement.setLong(3, usuarioId);
            statement.setString(4, metodoPago.name());
            statement.setDouble(5, total);
            statement.setDouble(6, costo);
            statement.setDouble(7, ganancia);
            statement.setString(8, motivo);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite no devolvió el id de la devolución.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertDevolucionDetalle(Connection connection, long devolucionId, LineaCalculada calculada)
            throws SQLException {
        String sql = """
                INSERT INTO devolucion_detalle (
                    devolucion_id, venta_detalle_id, producto_id, cantidad,
                    precio_unitario, costo_unitario, subtotal, ganancia_revertida
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, devolucionId);
            statement.setLong(2, calculada.linea().ventaDetalleId());
            statement.setLong(3, calculada.linea().productoId());
            statement.setDouble(4, calculada.cantidad());
            statement.setDouble(5, calculada.linea().precioUnitario());
            statement.setDouble(6, calculada.linea().costoUnitario());
            statement.setDouble(7, calculada.subtotal());
            statement.setDouble(8, calculada.ganancia());
            statement.executeUpdate();
        }
    }

    private void insertStockMovement(
            Connection connection,
            long productoId,
            double cantidad,
            String motivo,
            String referencia,
            long usuarioId,
            String observacion
    ) throws SQLException {
        String sql = """
                INSERT INTO mov_stock (
                    producto_id, tipo, motivo, cantidad, referencia, usuario_id, observacion
                ) VALUES (?, 'ENTRADA', ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productoId);
            statement.setString(2, motivo);
            statement.setDouble(3, cantidad);
            statement.setString(4, referencia);
            statement.setLong(5, usuarioId);
            statement.setString(6, observacion);
            statement.executeUpdate();
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private LocalDateTime parseDate(String value) {
        LocalDateTime utc = LocalDateTime.parse(value, SQLITE_DATE);
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private record LineaCalculada(
            VentaPostventaLinea linea,
            double cantidad,
            double subtotal,
            double ganancia
    ) {
    }
}
