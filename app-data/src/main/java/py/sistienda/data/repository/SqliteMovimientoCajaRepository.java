package py.sistienda.data.repository;

import py.sistienda.core.model.MovimientoCaja;
import py.sistienda.core.model.ResumenMovimientosCaja;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.core.repository.MovimientoCajaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteMovimientoCajaRepository implements MovimientoCajaRepository {
    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteMovimientoCajaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public MovimientoCaja create(long cajaSesionId, long usuarioId, TipoMovimientoCaja tipo,
                                 String categoria, String concepto, double monto, String referencia) {
        String sql = """
                INSERT INTO caja_movimiento
                    (caja_sesion_id, usuario_id, tipo, categoria, concepto, monto, referencia)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, usuarioId);
            statement.setString(3, tipo.name());
            statement.setString(4, categoria);
            statement.setString(5, concepto);
            statement.setDouble(6, monto);
            statement.setString(7, referencia);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del movimiento.");
                return findById(connection, keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo registrar el movimiento de caja.", e);
        }
    }

    @Override
    public List<MovimientoCaja> findByCaja(long cajaSesionId) {
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
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            try (var result = statement.executeQuery()) {
                List<MovimientoCaja> items = new ArrayList<>();
                while (result.next()) items.add(map(result));
                return items;
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudieron cargar los movimientos de caja.", e);
        }
    }

    @Override
    public ResumenMovimientosCaja summary(long cajaSesionId) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN tipo = 'INGRESO' THEN monto ELSE 0 END), 0) AS ingresos,
                    COALESCE(SUM(CASE WHEN tipo = 'EGRESO' THEN monto ELSE 0 END), 0) AS egresos
                FROM caja_movimiento
                WHERE caja_sesion_id = ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return ResumenMovimientosCaja.vacio();
                return new ResumenMovimientosCaja(result.getDouble("ingresos"), result.getDouble("egresos"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el resumen de movimientos de caja.", e);
        }
    }

    private MovimientoCaja findById(java.sql.Connection connection, long id) throws SQLException {
        String sql = """
                SELECT m.id, m.caja_sesion_id, m.usuario_id,
                       datetime(m.fecha, 'localtime') AS fecha_local,
                       m.tipo, m.categoria, m.concepto, m.monto, m.referencia,
                       u.username AS usuario
                FROM caja_movimiento m
                JOIN usuario u ON u.id = m.usuario_id
                WHERE m.id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("No se encontró el movimiento de caja.");
                return map(result);
            }
        }
    }

    private MovimientoCaja map(ResultSet result) throws SQLException {
        return new MovimientoCaja(
                result.getLong("id"),
                result.getLong("caja_sesion_id"),
                result.getLong("usuario_id"),
                result.getString("usuario"),
                LocalDateTime.parse(result.getString("fecha_local"), SQLITE_DATE),
                TipoMovimientoCaja.valueOf(result.getString("tipo")),
                result.getString("categoria"),
                result.getString("concepto"),
                result.getDouble("monto"),
                result.getString("referencia")
        );
    }
}
