package py.sistienda.data.repository;

import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.repository.CajaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

public final class SqliteCajaRepository implements CajaRepository {

    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteCajaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public Optional<CajaSesion> findOpenByUser(long usuarioId) {
        String sql = """
                SELECT id, usuario_id,
                       datetime(fecha_apertura, 'localtime') AS fecha_apertura_local,
                       CASE WHEN fecha_cierre IS NULL THEN NULL ELSE datetime(fecha_cierre, 'localtime') END AS fecha_cierre_local,
                       monto_apertura, monto_cierre, estado, notas
                FROM caja_sesion
                WHERE usuario_id = ? AND estado = 'ABIERTA'
                ORDER BY id DESC
                LIMIT 1
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, usuarioId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar la caja abierta.", e);
        }
    }

    @Override
    public CajaSesion open(long usuarioId, double montoApertura, String notas) {
        String sql = "INSERT INTO caja_sesion (usuario_id, monto_apertura, estado, notas) VALUES (?, ?, 'ABIERTA', ?)";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, usuarioId);
            statement.setDouble(2, montoApertura);
            statement.setString(3, notas);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite no devolvió el id de la caja.");
                }
                return findById(connection, keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo abrir la caja.", e);
        }
    }

    @Override
    public CajaSesion close(long cajaSesionId, double montoCierre, String notas) {
        String sql = """
                UPDATE caja_sesion
                SET fecha_cierre = datetime('now'), monto_cierre = ?, estado = 'CERRADA', notas = ?
                WHERE id = ? AND estado = 'ABIERTA'
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, montoCierre);
            statement.setString(2, notas);
            statement.setLong(3, cajaSesionId);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new SQLException("La caja ya no está abierta.");
            }
            return findById(connection, cajaSesionId);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cerrar la caja.", e);
        }
    }

    @Override
    public ResumenVentasCaja salesSummary(long cajaSesionId) {
        String sql = """
                WITH pagos AS (
                    SELECT vp.metodo_pago, vp.monto AS importe
                    FROM venta v
                    JOIN venta_pago vp ON vp.venta_id = v.id
                    WHERE v.caja_sesion_id = ? AND v.anulada = 0
                    UNION ALL
                    SELECT dp.metodo_pago, -dp.monto AS importe
                    FROM devolucion d
                    JOIN devolucion_pago dp ON dp.devolucion_id = d.id
                    WHERE d.caja_sesion_id = ?
                ),
                ganancias AS (
                    SELECT ganancia_total AS importe
                    FROM venta
                    WHERE caja_sesion_id = ? AND anulada = 0
                    UNION ALL
                    SELECT -ganancia_revertida AS importe
                    FROM devolucion
                    WHERE caja_sesion_id = ?
                )
                SELECT
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'EFECTIVO' THEN importe ELSE 0 END) FROM pagos), 0) AS efectivo,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'TRANSFERENCIA' THEN importe ELSE 0 END) FROM pagos), 0) AS transferencia,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'TARJETA' THEN importe ELSE 0 END) FROM pagos), 0) AS tarjeta,
                    COALESCE((SELECT SUM(CASE WHEN metodo_pago = 'FIADO' THEN importe ELSE 0 END) FROM pagos), 0) AS fiado,
                    COALESCE((SELECT SUM(importe) FROM pagos), 0) AS total,
                    COALESCE((SELECT SUM(importe) FROM ganancias), 0) AS ganancia
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, cajaSesionId);
            statement.setLong(3, cajaSesionId);
            statement.setLong(4, cajaSesionId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return ResumenVentasCaja.vacio();
                }
                return new ResumenVentasCaja(
                        result.getDouble("efectivo"),
                        result.getDouble("transferencia"),
                        result.getDouble("tarjeta"),
                        result.getDouble("fiado"),
                        result.getDouble("total"),
                        result.getDouble("ganancia")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen de ventas de la caja.", e);
        }
    }

    private CajaSesion findById(java.sql.Connection connection, long id) throws SQLException {
        String sql = """
                SELECT id, usuario_id,
                       datetime(fecha_apertura, 'localtime') AS fecha_apertura_local,
                       CASE WHEN fecha_cierre IS NULL THEN NULL ELSE datetime(fecha_cierre, 'localtime') END AS fecha_cierre_local,
                       monto_apertura, monto_cierre, estado, notas
                FROM caja_sesion
                WHERE id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No se encontró la sesión de caja.");
                }
                return map(result);
            }
        }
    }

    private CajaSesion map(ResultSet result) throws SQLException {
        String cierreRaw = result.getString("fecha_cierre_local");
        Double montoCierre = result.getObject("monto_cierre") == null ? null : result.getDouble("monto_cierre");
        return new CajaSesion(
                result.getLong("id"),
                result.getLong("usuario_id"),
                LocalDateTime.parse(result.getString("fecha_apertura_local"), SQLITE_DATE),
                cierreRaw == null ? null : LocalDateTime.parse(cierreRaw, SQLITE_DATE),
                result.getDouble("monto_apertura"),
                montoCierre,
                EstadoCaja.valueOf(result.getString("estado")),
                result.getString("notas")
        );
    }
}
