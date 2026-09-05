package py.sistienda.data.repository;

import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.ReporteDiario;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
                SELECT
                    COALESCE(SUM(CASE WHEN anulada = 0 THEN total ELSE 0 END), 0) AS ventas,
                    COALESCE(SUM(CASE WHEN anulada = 0 THEN ganancia_total ELSE 0 END), 0) AS ganancia,
                    COALESCE(SUM(CASE WHEN anulada = 0 THEN 1 ELSE 0 END), 0) AS tickets,
                    COALESCE(AVG(CASE WHEN anulada = 0 THEN total END), 0) AS ticket_promedio,
                    COALESCE(SUM(CASE WHEN anulada = 0 AND metodo_pago = 'EFECTIVO' THEN total ELSE 0 END), 0) AS efectivo,
                    COALESCE(SUM(CASE WHEN anulada = 0 AND metodo_pago = 'TRANSFERENCIA' THEN total ELSE 0 END), 0) AS transferencia,
                    COALESCE(SUM(CASE WHEN anulada = 0 AND metodo_pago = 'TARJETA' THEN total ELSE 0 END), 0) AS tarjeta
                FROM venta
                WHERE date(fecha, 'localtime') = ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, fecha.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ReporteDiario(fecha, 0, 0, 0, 0, 0, 0, 0);
                }
                return new ReporteDiario(
                        fecha,
                        result.getDouble("ventas"),
                        result.getDouble("ganancia"),
                        result.getLong("tickets"),
                        result.getDouble("ticket_promedio"),
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
        String sql = """
                SELECT v.id, v.nro_ticket, v.fecha, u.username, v.metodo_pago,
                       v.total, v.ganancia_total, v.anulada
                FROM venta v
                JOIN usuario u ON u.id = v.usuario_id
                WHERE date(v.fecha, 'localtime') = ?
                ORDER BY v.fecha DESC, v.id DESC
                """;
        List<VentaResumen> ventas = new ArrayList<>();
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, fecha.toString());
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
                            result.getInt("anulada") != 0
                    ));
                }
            }
            return List.copyOf(ventas);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el historial de ventas.", e);
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
        return LocalDateTime.parse(value, SQLITE_DATE);
    }
}
