package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.InventarioConteoDetalle;
import py.sistienda.core.model.InventarioConteoItem;
import py.sistienda.core.model.InventarioConteoResultado;
import py.sistienda.core.model.InventarioConteoResumen;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.InventarioRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteInventarioRepository implements InventarioRepository {
    private static final double EPSILON = 0.000001d;
    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteInventarioRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public InventarioConteoResultado registrar(long usuarioId, String motivo, String observacion,
                                               List<InventarioConteoItem> items) {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                asegurarUsuario(connection, usuarioId);
                int ajustados = (int) items.stream().filter(item -> Math.abs(item.diferencia()) > EPSILON).count();
                long inventarioId = insertarCabecera(connection, usuarioId, motivo, observacion, items.size(), ajustados);

                for (InventarioConteoItem item : items) {
                    ProductoActual actual = cargarProducto(connection, item.productoId());
                    if (!actual.activo()) {
                        throw new ValidationException("El producto “" + actual.nombre() + "” ya no está activo.");
                    }
                    if (actual.unidadMedida() != item.unidadMedida()) {
                        throw new ValidationException("Cambió la unidad de medida de “" + actual.nombre() + "”. Recargá el inventario.");
                    }
                    if (Math.abs(actual.stock() - item.stockSistema()) > EPSILON) {
                        throw new ValidationException("El stock de “" + actual.nombre()
                                + "” cambió mientras realizabas el conteo. Recargá el inventario antes de confirmar.");
                    }

                    double diferencia = item.stockFisico() - actual.stock();
                    insertarDetalle(connection, inventarioId, item.productoId(), actual.nombre(),
                            actual.unidadMedida(), actual.stock(), item.stockFisico(), diferencia);
                    if (Math.abs(diferencia) > EPSILON) {
                        registrarAjusteStock(connection, inventarioId, usuarioId, item.productoId(),
                                diferencia, motivo, observacion);
                    }
                }

                InventarioConteoResultado resultado = cargarResultado(connection, inventarioId);
                connection.commit();
                return resultado;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                if (e instanceof ValidationException validation) throw validation;
                throw e;
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("stock insuficiente")) {
                throw new ValidationException("El stock cambió durante el conteo. Recargá el inventario e intentá nuevamente.");
            }
            throw new RuntimeException("No se pudo registrar el inventario físico.", e);
        }
    }

    @Override
    public List<InventarioConteoResumen> recientes(int limite) {
        String sql = """
                SELECT i.id, i.fecha, u.username, i.motivo, i.observacion,
                       i.total_contados, i.total_ajustados
                FROM inventario_conteo i
                JOIN usuario u ON u.id = i.usuario_id
                ORDER BY i.id DESC
                LIMIT ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                List<InventarioConteoResumen> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new InventarioConteoResumen(
                            result.getLong("id"),
                            parseDate(result.getString("fecha")),
                            result.getString("username"),
                            result.getString("motivo"),
                            result.getString("observacion"),
                            result.getInt("total_contados"),
                            result.getInt("total_ajustados")
                    ));
                }
                return List.copyOf(items);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el historial de inventarios.", e);
        }
    }

    @Override
    public List<InventarioConteoDetalle> detalle(long inventarioId) {
        String sql = """
                SELECT producto_nombre, unidad_medida, stock_sistema, stock_fisico, diferencia
                FROM inventario_conteo_detalle
                WHERE inventario_id = ?
                ORDER BY producto_nombre COLLATE NOCASE
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, inventarioId);
            try (var result = statement.executeQuery()) {
                List<InventarioConteoDetalle> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new InventarioConteoDetalle(
                            result.getString("producto_nombre"),
                            UnidadMedida.valueOf(result.getString("unidad_medida")),
                            result.getDouble("stock_sistema"),
                            result.getDouble("stock_fisico"),
                            result.getDouble("diferencia")
                    ));
                }
                return List.copyOf(items);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el detalle del inventario.", e);
        }
    }

    private long insertarCabecera(Connection connection, long usuarioId, String motivo, String observacion,
                                  int contados, int ajustados) throws SQLException {
        String sql = """
                INSERT INTO inventario_conteo
                    (usuario_id, motivo, observacion, total_contados, total_ajustados)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, usuarioId);
            statement.setString(2, motivo);
            statement.setString(3, observacion);
            statement.setInt(4, contados);
            statement.setInt(5, ajustados);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del inventario.");
                return keys.getLong(1);
            }
        }
    }

    private void insertarDetalle(Connection connection, long inventarioId, long productoId, String nombre,
                                 UnidadMedida unidad, double sistema, double fisico, double diferencia) throws SQLException {
        String sql = """
                INSERT INTO inventario_conteo_detalle
                    (inventario_id, producto_id, producto_nombre, unidad_medida,
                     stock_sistema, stock_fisico, diferencia)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, inventarioId);
            statement.setLong(2, productoId);
            statement.setString(3, nombre);
            statement.setString(4, unidad.name());
            statement.setDouble(5, sistema);
            statement.setDouble(6, fisico);
            statement.setDouble(7, diferencia);
            statement.executeUpdate();
        }
    }

    private void registrarAjusteStock(Connection connection, long inventarioId, long usuarioId, long productoId,
                                      double diferencia, String motivo, String observacion) throws SQLException {
        String sql = """
                INSERT INTO mov_stock
                    (producto_id, tipo, motivo, cantidad, referencia, usuario_id, observacion)
                VALUES (?, ?, 'AJUSTE INVENTARIO', ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productoId);
            statement.setString(2, diferencia > 0 ? "ENTRADA" : "SALIDA");
            statement.setDouble(3, Math.abs(diferencia));
            statement.setString(4, "Inventario #" + inventarioId);
            statement.setLong(5, usuarioId);
            String detalle = observacion == null ? motivo : motivo + " · " + observacion;
            statement.setString(6, detalle);
            statement.executeUpdate();
        }
    }

    private ProductoActual cargarProducto(Connection connection, long productoId) throws SQLException {
        String sql = "SELECT nombre, unidad_medida, stock_actual, activo FROM producto WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productoId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ValidationException("Uno de los productos ya no existe.");
                return new ProductoActual(
                        result.getString("nombre"),
                        UnidadMedida.valueOf(result.getString("unidad_medida")),
                        result.getDouble("stock_actual"),
                        result.getInt("activo") != 0
                );
            }
        }
    }

    private void asegurarUsuario(Connection connection, long usuarioId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM usuario WHERE id = ? AND activo = 1")) {
            statement.setLong(1, usuarioId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new ValidationException("El usuario ya no está activo.");
            }
        }
    }

    private InventarioConteoResultado cargarResultado(Connection connection, long inventarioId) throws SQLException {
        String sql = """
                SELECT i.id, i.fecha, u.username, i.motivo, i.total_contados, i.total_ajustados
                FROM inventario_conteo i
                JOIN usuario u ON u.id = i.usuario_id
                WHERE i.id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, inventarioId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("No se encontró el inventario registrado.");
                return new InventarioConteoResultado(
                        result.getLong("id"),
                        parseDate(result.getString("fecha")),
                        result.getString("username"),
                        result.getString("motivo"),
                        result.getInt("total_contados"),
                        result.getInt("total_ajustados")
                );
            }
        }
    }

    private LocalDateTime parseDate(String value) {
        LocalDateTime utc = LocalDateTime.parse(value, SQLITE_DATE);
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private record ProductoActual(String nombre, UnidadMedida unidadMedida, double stock, boolean activo) {
    }
}
