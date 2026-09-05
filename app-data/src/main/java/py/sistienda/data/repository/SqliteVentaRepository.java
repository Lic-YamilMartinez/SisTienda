package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.repository.VentaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

public final class SqliteVentaRepository implements VentaRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteVentaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public VentaResultado register(
            long cajaSesionId,
            long usuarioId,
            MetodoPago metodoPago,
            double recibido,
            double vuelto,
            List<LineaVenta> lineas
    ) {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                ensureCashOpen(connection, cajaSesionId, usuarioId);

                int nroTicket = nextTicket(connection);
                double total = lineas.stream().mapToDouble(LineaVenta::subtotal).sum();
                double ganancia = lineas.stream().mapToDouble(LineaVenta::ganancia).sum();

                long ventaId = insertVenta(
                        connection,
                        cajaSesionId,
                        usuarioId,
                        metodoPago,
                        recibido,
                        vuelto,
                        total,
                        ganancia,
                        nroTicket
                );

                for (LineaVenta linea : lineas) {
                    insertDetail(connection, ventaId, linea);
                    insertStockMovement(connection, usuarioId, nroTicket, linea);
                }

                connection.commit();
                return new VentaResultado(
                        ventaId,
                        nroTicket,
                        total,
                        recibido,
                        vuelto,
                        ganancia,
                        metodoPago
                );
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                if (isStockError(e)) {
                    throw new ValidationException("Stock insuficiente para completar la venta. Actualizá el catálogo e intentá nuevamente.");
                }
                if (e instanceof ValidationException validationException) {
                    throw validationException;
                }
                throw new RuntimeException("No se pudo registrar la venta.", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo abrir la transacción de venta.", e);
        }
    }

    private void ensureCashOpen(Connection connection, long cajaSesionId, long usuarioId) throws SQLException {
        String sql = "SELECT 1 FROM caja_sesion WHERE id = ? AND usuario_id = ? AND estado = 'ABIERTA'";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, usuarioId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new ValidationException("La caja ya no está abierta.");
                }
            }
        }
    }

    private int nextTicket(Connection connection) throws SQLException {
        try (var update = connection.prepareStatement(
                "UPDATE secuencia SET valor = valor + 1 WHERE clave = 'TICKET'")) {
            if (update.executeUpdate() != 1) {
                throw new SQLException("No se pudo actualizar la secuencia de ticket.");
            }
        }

        try (var query = connection.prepareStatement(
                "SELECT valor FROM secuencia WHERE clave = 'TICKET'");
             var result = query.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("No se encontró la secuencia de ticket.");
            }
            return result.getInt(1);
        }
    }

    private long insertVenta(
            Connection connection,
            long cajaSesionId,
            long usuarioId,
            MetodoPago metodoPago,
            double recibido,
            double vuelto,
            double total,
            double ganancia,
            int nroTicket
    ) throws SQLException {
        String sql = """
                INSERT INTO venta (
                    caja_sesion_id, usuario_id, total, total_lista, ganancia_total,
                    metodo_pago, recibido, vuelto, nro_ticket, anulada
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """;
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, usuarioId);
            statement.setDouble(3, total);
            statement.setDouble(4, total);
            statement.setDouble(5, ganancia);
            statement.setString(6, metodoPago.name());
            statement.setDouble(7, recibido);
            statement.setDouble(8, vuelto);
            statement.setInt(9, nroTicket);
            statement.executeUpdate();

            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite no devolvió el id de la venta.");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertDetail(Connection connection, long ventaId, LineaVenta linea) throws SQLException {
        String sql = """
                INSERT INTO venta_detalle (
                    venta_id, producto_id, cantidad, precio_unitario, precio_lista,
                    costo_unitario, subtotal, ganancia_linea
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ventaId);
            statement.setLong(2, linea.producto().id());
            statement.setDouble(3, linea.cantidad());
            statement.setDouble(4, linea.producto().precioVenta());
            statement.setDouble(5, linea.producto().precioVenta());
            statement.setDouble(6, linea.producto().costo());
            statement.setDouble(7, linea.subtotal());
            statement.setDouble(8, linea.ganancia());
            statement.executeUpdate();
        }
    }

    private void insertStockMovement(
            Connection connection,
            long usuarioId,
            int nroTicket,
            LineaVenta linea
    ) throws SQLException {
        String sql = """
                INSERT INTO mov_stock (
                    producto_id, tipo, motivo, cantidad, referencia, usuario_id, observacion
                ) VALUES (?, 'SALIDA', 'VENTA', ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, linea.producto().id());
            statement.setDouble(2, linea.cantidad());
            statement.setString(3, "TICKET-" + nroTicket);
            statement.setLong(4, usuarioId);
            statement.setString(5, "Venta #" + nroTicket);
            statement.executeUpdate();
        }
    }

    private boolean isStockError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("stock insuficiente")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
