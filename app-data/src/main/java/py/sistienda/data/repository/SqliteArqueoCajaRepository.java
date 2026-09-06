package py.sistienda.data.repository;

import py.sistienda.core.model.ArqueoCajaDetalle;
import py.sistienda.core.model.ArqueoCajaResumen;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.MovimientoCaja;
import py.sistienda.core.model.ResumenMovimientosCaja;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.core.repository.ArqueoCajaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteArqueoCajaRepository implements ArqueoCajaRepository {
    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteArqueoCajaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public List<ArqueoCajaResumen> findRecent(int limit) {
        String sql = baseSummarySql() + " ORDER BY c.id DESC LIMIT ?";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (var result = statement.executeQuery()) {
                List<ArqueoCajaResumen> items = new ArrayList<>();
                while (result.next()) {
                    items.add(mapResumen(result));
                }
                return items;
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el historial de cajas.", e);
        }
    }

    @Override
    public ArqueoCajaDetalle findDetail(long cajaId) {
        String sql = baseSummarySql() + " WHERE c.id = ?";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No se encontró la caja seleccionada.");
                }

                ResumenVentasCaja ventas = new ResumenVentasCaja(
                        result.getDouble("ventas_efectivo"),
                        result.getDouble("ventas_transferencia"),
                        result.getDouble("ventas_tarjeta"),
                        result.getDouble("ventas_total")
                );
                ResumenMovimientosCaja movimientos = new ResumenMovimientosCaja(
                        result.getDouble("ingresos"),
                        result.getDouble("egresos")
                );
                double esperado = result.getDouble("monto_apertura")
                        + ventas.efectivo() + movimientos.ingresos() - movimientos.egresos();
                Double contado = nullableDouble(result, "monto_cierre");
                Double diferencia = contado == null ? null : contado - esperado;

                return new ArqueoCajaDetalle(
                        result.getLong("id"),
                        result.getString("usuario"),
                        parseDate(result.getString("fecha_apertura_local")),
                        parseNullableDate(result.getString("fecha_cierre_local")),
                        EstadoCaja.valueOf(result.getString("estado")),
                        result.getDouble("monto_apertura"),
                        ventas,
                        movimientos,
                        result.getLong("tickets"),
                        esperado,
                        contado,
                        diferencia,
                        result.getString("notas"),
                        findMovimientos(connection, cajaId)
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el arqueo de caja.", e);
        }
    }

    private String baseSummarySql() {
        return """
                WITH ventas AS (
                    SELECT caja_sesion_id,
                           COALESCE(SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN total ELSE 0 END), 0) AS efectivo,
                           COALESCE(SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN total ELSE 0 END), 0) AS transferencia,
                           COALESCE(SUM(CASE WHEN metodo_pago = 'TARJETA' THEN total ELSE 0 END), 0) AS tarjeta,
                           COALESCE(SUM(total), 0) AS total,
                           COUNT(*) AS tickets
                    FROM venta
                    WHERE anulada = 0
                    GROUP BY caja_sesion_id
                ),
                movimientos AS (
                    SELECT caja_sesion_id,
                           COALESCE(SUM(CASE WHEN tipo = 'INGRESO' THEN monto ELSE 0 END), 0) AS ingresos,
                           COALESCE(SUM(CASE WHEN tipo = 'EGRESO' THEN monto ELSE 0 END), 0) AS egresos
                    FROM caja_movimiento
                    GROUP BY caja_sesion_id
                )
                SELECT c.id,
                       u.username AS usuario,
                       datetime(c.fecha_apertura, 'localtime') AS fecha_apertura_local,
                       CASE WHEN c.fecha_cierre IS NULL THEN NULL
                            ELSE datetime(c.fecha_cierre, 'localtime') END AS fecha_cierre_local,
                       c.estado,
                       c.monto_apertura,
                       c.monto_cierre,
                       c.notas,
                       COALESCE(v.efectivo, 0) AS ventas_efectivo,
                       COALESCE(v.transferencia, 0) AS ventas_transferencia,
                       COALESCE(v.tarjeta, 0) AS ventas_tarjeta,
                       COALESCE(v.total, 0) AS ventas_total,
                       COALESCE(v.tickets, 0) AS tickets,
                       COALESCE(m.ingresos, 0) AS ingresos,
                       COALESCE(m.egresos, 0) AS egresos
                FROM caja_sesion c
                JOIN usuario u ON u.id = c.usuario_id
                LEFT JOIN ventas v ON v.caja_sesion_id = c.id
                LEFT JOIN movimientos m ON m.caja_sesion_id = c.id
                """;
    }

    private ArqueoCajaResumen mapResumen(ResultSet result) throws SQLException {
        double efectivo = result.getDouble("ventas_efectivo");
        double ingresos = result.getDouble("ingresos");
        double egresos = result.getDouble("egresos");
        double esperado = result.getDouble("monto_apertura") + efectivo + ingresos - egresos;
        Double contado = nullableDouble(result, "monto_cierre");
        Double diferencia = contado == null ? null : contado - esperado;

        return new ArqueoCajaResumen(
                result.getLong("id"),
                result.getString("usuario"),
                parseDate(result.getString("fecha_apertura_local")),
                parseNullableDate(result.getString("fecha_cierre_local")),
                EstadoCaja.valueOf(result.getString("estado")),
                result.getDouble("monto_apertura"),
                result.getDouble("ventas_total"),
                result.getLong("tickets"),
                esperado,
                contado,
                diferencia
        );
    }

    private List<MovimientoCaja> findMovimientos(Connection connection, long cajaId) throws SQLException {
        String sql = """
                SELECT m.id, m.caja_sesion_id, m.usuario_id,
                       datetime(m.fecha, 'localtime') AS fecha_local,
                       m.tipo, m.categoria, m.concepto, m.monto, m.referencia,
                       u.username AS usuario
                FROM caja_movimiento m
                JOIN usuario u ON u.id = m.usuario_id
                WHERE m.caja_sesion_id = ?
                ORDER BY m.id DESC
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaId);
            try (var result = statement.executeQuery()) {
                List<MovimientoCaja> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new MovimientoCaja(
                            result.getLong("id"),
                            result.getLong("caja_sesion_id"),
                            result.getLong("usuario_id"),
                            result.getString("usuario"),
                            parseDate(result.getString("fecha_local")),
                            TipoMovimientoCaja.valueOf(result.getString("tipo")),
                            result.getString("categoria"),
                            result.getString("concepto"),
                            result.getDouble("monto"),
                            result.getString("referencia")
                    ));
                }
                return items;
            }
        }
    }

    private Double nullableDouble(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? null : result.getDouble(column);
    }

    private LocalDateTime parseDate(String value) {
        return LocalDateTime.parse(value, SQLITE_DATE);
    }

    private LocalDateTime parseNullableDate(String value) {
        return value == null ? null : parseDate(value);
    }
}
