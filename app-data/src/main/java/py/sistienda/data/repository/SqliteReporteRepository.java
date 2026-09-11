package py.sistienda.data.repository;

import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.ProductoVendidoResumen;
import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.ReporteLineaTiempo;
import py.sistienda.core.model.ReportePeriodoResumen;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaDetalleItem;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.repository.ReporteRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SqliteReporteRepository implements ReporteRepository {

    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteReporteRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public ReporteDiario resumenDiario(LocalDate fecha) {
        String sql = """
                WITH movimientos AS (
                    SELECT fecha, metodo_pago, total AS ventas, ganancia_total AS ganancia, 1 AS ticket
                    FROM venta
                    WHERE anulada = 0
                    UNION ALL
                    SELECT fecha, metodo_pago, -total AS ventas, -ganancia_revertida AS ganancia, 0 AS ticket
                    FROM devolucion
                )
                SELECT
                    COALESCE(SUM(ventas), 0) AS ventas,
                    COALESCE(SUM(ganancia), 0) AS ganancia,
                    COALESCE(SUM(ticket), 0) AS tickets,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN ventas ELSE 0 END), 0) AS efectivo,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN ventas ELSE 0 END), 0) AS transferencia,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'TARJETA' THEN ventas ELSE 0 END), 0) AS tarjeta
                FROM movimientos
                WHERE date(fecha, 'localtime') = ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, fecha.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ReporteDiario(fecha, 0, 0, 0, 0, 0, 0, 0);
                }
                double ventas = result.getDouble("ventas");
                long tickets = result.getLong("tickets");
                return new ReporteDiario(
                        fecha,
                        ventas,
                        result.getDouble("ganancia"),
                        tickets,
                        tickets == 0 ? 0d : ventas / tickets,
                        result.getDouble("efectivo"),
                        result.getDouble("transferencia"),
                        result.getDouble("tarjeta")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen diario.", e);
        }
    }

    @Override
    public List<VentaResumen> listarVentas(LocalDate fecha) {
        return listarVentas(fecha, fecha, null, 1000);
    }

    @Override
    public ReportePeriodoResumen resumenPeriodo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
        String filtroPago = metodoPago == null ? "" : " AND metodo_pago = ? ";
        String movimientosComerciales = """
                SELECT fecha, metodo_pago,
                       total AS ventas,
                       total - ganancia_total AS costo,
                       ganancia_total AS ganancia,
                       1 AS ticket
                FROM venta
                WHERE anulada = 0
                UNION ALL
                SELECT fecha, metodo_pago,
                       -total AS ventas,
                       -costo_total AS costo,
                       -ganancia_revertida AS ganancia,
                       0 AS ticket
                FROM devolucion
                """;

        String filteredSql = """
                SELECT
                    COALESCE(SUM(ventas), 0) AS ventas,
                    COALESCE(SUM(costo), 0) AS costo,
                    COALESCE(SUM(ganancia), 0) AS ganancia,
                    COALESCE(SUM(ticket), 0) AS tickets
                FROM (
                """ + movimientosComerciales + """
                ) m
                WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                """ + filtroPago;

        String globalSalesSql = """
                SELECT
                    COALESCE(SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN ventas ELSE 0 END), 0) AS efectivo,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN ventas ELSE 0 END), 0) AS transferencia,
                    COALESCE(SUM(CASE WHEN metodo_pago = 'TARJETA' THEN ventas ELSE 0 END), 0) AS tarjeta,
                    COALESCE(SUM(ganancia), 0) AS ganancia_global
                FROM (
                """ + movimientosComerciales + """
                ) m
                WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                """;

        String movementsSql = """
                SELECT
                    COALESCE(SUM(CASE WHEN tipo = 'INGRESO' THEN monto ELSE 0 END), 0) AS ingresos,
                    COALESCE(SUM(CASE WHEN tipo = 'EGRESO' THEN monto ELSE 0 END), 0) AS egresos
                FROM caja_movimiento
                WHERE date(fecha, 'localtime') BETWEEN ? AND ?
                """;

        try (var connection = connectionFactory.open()) {
            double ventas;
            double costo;
            double ganancia;
            long tickets;
            try (var statement = connection.prepareStatement(filteredSql)) {
                statement.setString(1, desde.toString());
                statement.setString(2, hasta.toString());
                if (metodoPago != null) statement.setString(3, metodoPago.name());
                try (var result = statement.executeQuery()) {
                    result.next();
                    ventas = result.getDouble("ventas");
                    costo = result.getDouble("costo");
                    ganancia = result.getDouble("ganancia");
                    tickets = result.getLong("tickets");
                }
            }

            double efectivo;
            double transferencia;
            double tarjeta;
            double gananciaGlobal;
            try (var statement = connection.prepareStatement(globalSalesSql)) {
                statement.setString(1, desde.toString());
                statement.setString(2, hasta.toString());
                try (var result = statement.executeQuery()) {
                    result.next();
                    efectivo = result.getDouble("efectivo");
                    transferencia = result.getDouble("transferencia");
                    tarjeta = result.getDouble("tarjeta");
                    gananciaGlobal = result.getDouble("ganancia_global");
                }
            }

            double ingresos;
            double egresos;
            try (var statement = connection.prepareStatement(movementsSql)) {
                statement.setString(1, desde.toString());
                statement.setString(2, hasta.toString());
                try (var result = statement.executeQuery()) {
                    result.next();
                    ingresos = result.getDouble("ingresos");
                    egresos = result.getDouble("egresos");
                }
            }

            return new ReportePeriodoResumen(
                    desde,
                    hasta,
                    metodoPago,
                    ventas,
                    costo,
                    ganancia,
                    tickets,
                    tickets == 0 ? 0d : ventas / tickets,
                    efectivo,
                    transferencia,
                    tarjeta,
                    ingresos,
                    egresos,
                    gananciaGlobal + ingresos - egresos
            );
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen del período.", e);
        }
    }

    @Override
    public List<ReporteLineaTiempo> lineaTiempo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
        boolean mensual = ChronoUnit.DAYS.between(desde, hasta) > 62;
        String bucket = mensual
                ? "strftime('%Y-%m-01', fecha, 'localtime')"
                : "date(fecha, 'localtime')";
        String filtroPago = metodoPago == null ? "" : " AND metodo_pago = ? ";
        String sql = "SELECT " + bucket + " AS periodo, "
                + "COALESCE(SUM(ventas), 0) AS ventas, "
                + "COALESCE(SUM(ganancia), 0) AS ganancia, "
                + "COALESCE(SUM(ticket), 0) AS tickets "
                + "FROM ("
                + " SELECT fecha, metodo_pago, total AS ventas, ganancia_total AS ganancia, 1 AS ticket"
                + " FROM venta WHERE anulada = 0"
                + " UNION ALL"
                + " SELECT fecha, metodo_pago, -total AS ventas, -ganancia_revertida AS ganancia, 0 AS ticket"
                + " FROM devolucion"
                + ") m "
                + "WHERE date(fecha, 'localtime') BETWEEN ? AND ? "
                + filtroPago
                + "GROUP BY periodo ORDER BY periodo";

        Map<LocalDate, ReporteLineaTiempo> encontrados = new LinkedHashMap<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, desde.toString());
            statement.setString(2, hasta.toString());
            if (metodoPago != null) statement.setString(3, metodoPago.name());
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    LocalDate periodo = LocalDate.parse(result.getString("periodo"));
                    encontrados.put(periodo, new ReporteLineaTiempo(
                            periodo,
                            result.getDouble("ventas"),
                            result.getDouble("ganancia"),
                            result.getLong("tickets")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar la línea de tiempo del reporte.", e);
        }

        List<ReporteLineaTiempo> timeline = new ArrayList<>();
        if (mensual) {
            LocalDate cursor = desde.withDayOfMonth(1);
            LocalDate fin = hasta.withDayOfMonth(1);
            while (!cursor.isAfter(fin)) {
                timeline.add(encontrados.getOrDefault(cursor, new ReporteLineaTiempo(cursor, 0, 0, 0)));
                cursor = cursor.plusMonths(1);
            }
        } else {
            LocalDate cursor = desde;
            while (!cursor.isAfter(hasta)) {
                timeline.add(encontrados.getOrDefault(cursor, new ReporteLineaTiempo(cursor, 0, 0, 0)));
                cursor = cursor.plusDays(1);
            }
        }
        return List.copyOf(timeline);
    }

    @Override
    public List<ProductoVendidoResumen> productosMasVendidos(
            LocalDate desde,
            LocalDate hasta,
            MetodoPago metodoPago,
            int limite
    ) {
        String filtroPago = metodoPago == null ? "" : " AND m.metodo_pago = ? ";
        String sql = """
                WITH movimientos_producto AS (
                    SELECT v.fecha, v.metodo_pago, d.producto_id,
                           d.cantidad AS cantidad,
                           d.subtotal AS ventas,
                           d.costo_unitario * d.cantidad AS costo,
                           d.ganancia_linea AS ganancia
                    FROM venta_detalle d
                    JOIN venta v ON v.id = d.venta_id
                    WHERE v.anulada = 0
                    UNION ALL
                    SELECT dv.fecha, dv.metodo_pago, dd.producto_id,
                           -dd.cantidad AS cantidad,
                           -dd.subtotal AS ventas,
                           -(dd.costo_unitario * dd.cantidad) AS costo,
                           -dd.ganancia_revertida AS ganancia
                    FROM devolucion_detalle dd
                    JOIN devolucion dv ON dv.id = dd.devolucion_id
                )
                SELECT p.id AS producto_id, p.nombre, p.unidad_medida,
                       COALESCE(SUM(m.cantidad), 0) AS cantidad,
                       COALESCE(SUM(m.ventas), 0) AS ventas,
                       COALESCE(SUM(m.costo), 0) AS costo,
                       COALESCE(SUM(m.ganancia), 0) AS ganancia
                FROM movimientos_producto m
                JOIN producto p ON p.id = m.producto_id
                WHERE date(m.fecha, 'localtime') BETWEEN ? AND ?
                """ + filtroPago + """
                GROUP BY p.id, p.nombre, p.unidad_medida
                HAVING ABS(SUM(m.ventas)) > 0.000001 OR ABS(SUM(m.cantidad)) > 0.000001
                ORDER BY ventas DESC, cantidad DESC
                LIMIT ?
                """;

        List<ProductoVendidoResumen> productos = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, desde.toString());
            statement.setString(index++, hasta.toString());
            if (metodoPago != null) statement.setString(index++, metodoPago.name());
            statement.setInt(index, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    productos.add(new ProductoVendidoResumen(
                            result.getLong("producto_id"),
                            result.getString("nombre"),
                            UnidadMedida.valueOf(result.getString("unidad_medida")),
                            result.getDouble("cantidad"),
                            result.getDouble("ventas"),
                            result.getDouble("costo"),
                            result.getDouble("ganancia")
                    ));
                }
            }
            return List.copyOf(productos);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el ranking de productos.", e);
        }
    }

    @Override
    public List<VentaResumen> listarVentas(
            LocalDate desde,
            LocalDate hasta,
            MetodoPago metodoPago,
            int limite
    ) {
        String filtroPago = metodoPago == null ? "" : " AND v.metodo_pago = ? ";
        String sql = """
                SELECT v.id, v.nro_ticket, v.fecha, u.username, v.metodo_pago,
                       v.total, v.ganancia_total, v.anulada,
                       COALESCE((SELECT SUM(dv.total) FROM devolucion dv WHERE dv.venta_id = v.id), 0) AS devuelto
                FROM venta v
                JOIN usuario u ON u.id = v.usuario_id
                WHERE date(v.fecha, 'localtime') BETWEEN ? AND ?
                """ + filtroPago + """
                ORDER BY v.fecha DESC, v.id DESC
                LIMIT ?
                """;
        List<VentaResumen> ventas = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, desde.toString());
            statement.setString(index++, hasta.toString());
            if (metodoPago != null) statement.setString(index++, metodoPago.name());
            statement.setInt(index, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    ventas.add(new VentaResumen(
                            result.getLong("id"),
                            result.getLong("nro_ticket"),
                            parseDate(result.getString("fecha")),
                            result.getString("username"),
                            MetodoPago.valueOf(result.getString("metodo_pago")),
                            result.getDouble("total"),
                            result.getDouble("ganancia_total"),
                            result.getDouble("devuelto"),
                            result.getInt("anulada") != 0
                    ));
                }
            }
            return List.copyOf(ventas);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el historial de ventas del período.", e);
        }
    }

    @Override
    public Optional<VentaDetalle> detalleVenta(long ventaId) {
        String saleSql = """
                SELECT v.id, v.nro_ticket, v.fecha, u.username, v.metodo_pago,
                       v.total, v.recibido, v.vuelto, v.ganancia_total, v.anulada
                FROM venta v
                JOIN usuario u ON u.id = v.usuario_id
                WHERE v.id = ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(saleSql)) {
            statement.setLong(1, ventaId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                List<VentaDetalleItem> items = cargarItems(connection, ventaId);
                return Optional.of(new VentaDetalle(
                        result.getLong("id"),
                        result.getLong("nro_ticket"),
                        parseDate(result.getString("fecha")),
                        result.getString("username"),
                        MetodoPago.valueOf(result.getString("metodo_pago")),
                        result.getDouble("total"),
                        result.getDouble("recibido"),
                        result.getDouble("vuelto"),
                        result.getDouble("ganancia_total"),
                        result.getInt("anulada") != 0,
                        items
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el detalle de la venta.", e);
        }
    }

    private List<VentaDetalleItem> cargarItems(Connection connection, long ventaId) throws SQLException {
        String sql = """
                SELECT p.nombre, d.cantidad, d.precio_unitario, d.subtotal
                FROM venta_detalle d
                JOIN producto p ON p.id = d.producto_id
                WHERE d.venta_id = ?
                ORDER BY d.id
                """;
        List<VentaDetalleItem> items = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ventaId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new VentaDetalleItem(
                            result.getString("nombre"),
                            result.getDouble("cantidad"),
                            result.getDouble("precio_unitario"),
                            result.getDouble("subtotal")
                    ));
                }
            }
        }
        return List.copyOf(items);
    }

    private LocalDateTime parseDate(String value) {
        LocalDateTime utc = LocalDateTime.parse(value, SQLITE_DATE);
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
