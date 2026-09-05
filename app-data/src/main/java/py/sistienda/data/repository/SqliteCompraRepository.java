package py.sistienda.data.repository;

import py.sistienda.core.model.CompraDetalle;
import py.sistienda.core.model.CompraDetalleItem;
import py.sistienda.core.model.CompraResumen;
import py.sistienda.core.model.LineaCompra;
import py.sistienda.core.model.Proveedor;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.CompraRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteCompraRepository implements CompraRepository {
    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SqliteConnectionFactory connectionFactory;

    public SqliteCompraRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public long registrar(Usuario usuario, Proveedor proveedor, String nroDocumento, List<LineaCompra> lineas, String observacion) {
        double total = lineas.stream().mapToDouble(LineaCompra::subtotal).sum();
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                long compraId = insertarCompra(connection, usuario, proveedor, nroDocumento, total, observacion);
                for (LineaCompra linea : lineas) {
                    insertarDetalle(connection, compraId, linea);
                    registrarEntradaStock(connection, compraId, nroDocumento, usuario, proveedor, linea);
                    actualizarCosto(connection, linea);
                }
                connection.commit();
                return compraId;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: compra.proveedor_id, compra.nro_documento")) {
                    throw new RuntimeException("Ya existe una compra de este proveedor con el mismo número de documento.", e);
                }
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo registrar la compra.", e);
        }
    }

    @Override
    public List<CompraResumen> listarRecientes(int limite) {
        String sql = """
                SELECT c.id, datetime(c.fecha, 'localtime') AS fecha_local,
                       p.nombre AS proveedor, c.nro_documento, c.total, u.username
                FROM compra c
                JOIN proveedor p ON p.id = c.proveedor_id
                JOIN usuario u ON u.id = c.usuario_id
                ORDER BY c.fecha DESC, c.id DESC
                LIMIT ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limite));
            try (var result = statement.executeQuery()) {
                List<CompraResumen> compras = new ArrayList<>();
                while (result.next()) {
                    compras.add(new CompraResumen(
                            result.getLong("id"),
                            parseDate(result.getString("fecha_local")),
                            result.getString("proveedor"),
                            result.getString("nro_documento"),
                            result.getDouble("total"),
                            result.getString("username")
                    ));
                }
                return List.copyOf(compras);
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar el historial de compras.", e);
        }
    }

    @Override
    public CompraDetalle detalle(long compraId) {
        String sql = """
                SELECT c.id, datetime(c.fecha, 'localtime') AS fecha_local,
                       c.nro_documento, c.total, c.observacion, u.username,
                       p.id AS proveedor_id, p.nombre, p.ruc, p.telefono, p.email, p.direccion, p.activo
                FROM compra c
                JOIN proveedor p ON p.id = c.proveedor_id
                JOIN usuario u ON u.id = c.usuario_id
                WHERE c.id = ?
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, compraId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("La compra seleccionada ya no existe.");
                }
                Proveedor proveedor = new Proveedor(
                        result.getLong("proveedor_id"), result.getString("nombre"), result.getString("ruc"),
                        result.getString("telefono"), result.getString("email"), result.getString("direccion"),
                        result.getInt("activo") == 1
                );
                return new CompraDetalle(
                        result.getLong("id"),
                        parseDate(result.getString("fecha_local")),
                        proveedor,
                        result.getString("nro_documento"),
                        result.getDouble("total"),
                        result.getString("username"),
                        result.getString("observacion"),
                        cargarItems(connection, compraId)
                );
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar el detalle de la compra.", e);
        }
    }

    private long insertarCompra(Connection connection, Usuario usuario, Proveedor proveedor, String documento,
                               double total, String observacion) throws Exception {
        String sql = "INSERT INTO compra (proveedor_id, usuario_id, nro_documento, total, observacion) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, proveedor.id());
            statement.setLong(2, usuario.id());
            statement.setString(3, documento);
            statement.setDouble(4, total);
            statement.setString(5, observacion);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new IllegalStateException("SQLite no devolvió el id de la compra.");
        }
    }

    private void insertarDetalle(Connection connection, long compraId, LineaCompra linea) throws Exception {
        String sql = "INSERT INTO compra_detalle (compra_id, producto_id, cantidad, costo_unitario, subtotal) VALUES (?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, compraId);
            statement.setLong(2, linea.producto().id());
            statement.setDouble(3, linea.cantidad());
            statement.setDouble(4, linea.costoUnitario());
            statement.setDouble(5, linea.subtotal());
            statement.executeUpdate();
        }
    }

    private void registrarEntradaStock(Connection connection, long compraId, String documento, Usuario usuario,
                                       Proveedor proveedor, LineaCompra linea) throws Exception {
        String sql = """
                INSERT INTO mov_stock (producto_id, tipo, motivo, cantidad, referencia, usuario_id, observacion)
                VALUES (?, 'ENTRADA', 'COMPRA', ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, linea.producto().id());
            statement.setDouble(2, linea.cantidad());
            String referencia = documento == null ? "Compra #" + compraId : documento;
            statement.setString(3, referencia);
            statement.setLong(4, usuario.id());
            statement.setString(5, "Proveedor: " + proveedor.nombre());
            statement.executeUpdate();
        }
    }

    private void actualizarCosto(Connection connection, LineaCompra linea) throws Exception {
        String sql = "UPDATE producto SET costo = ?, actualizado_en = datetime('now') WHERE id = ? AND activo = 1";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, linea.costoUnitario());
            statement.setLong(2, linea.producto().id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("No se pudo actualizar el costo de " + linea.producto().nombre() + ".");
            }
        }
    }

    private List<CompraDetalleItem> cargarItems(Connection connection, long compraId) throws Exception {
        String sql = """
                SELECT p.nombre, d.cantidad, d.costo_unitario, d.subtotal
                FROM compra_detalle d
                JOIN producto p ON p.id = d.producto_id
                WHERE d.compra_id = ?
                ORDER BY d.id
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, compraId);
            try (var result = statement.executeQuery()) {
                List<CompraDetalleItem> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new CompraDetalleItem(
                            result.getString("nombre"),
                            result.getDouble("cantidad"),
                            result.getDouble("costo_unitario"),
                            result.getDouble("subtotal")
                    ));
                }
                return List.copyOf(items);
            }
        }
    }

    private LocalDateTime parseDate(String value) {
        return LocalDateTime.parse(value, SQLITE_DATE);
    }
}
