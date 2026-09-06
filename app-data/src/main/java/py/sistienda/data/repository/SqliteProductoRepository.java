package py.sistienda.data.repository;

import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.ProductoRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SqliteProductoRepository implements ProductoRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteProductoRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public List<Producto> findAllActive() {
        String sql = baseSelect() + " WHERE p.activo = 1 ORDER BY p.nombre COLLATE NOCASE";
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Producto> productos = new ArrayList<>();
            while (resultSet.next()) productos.add(map(resultSet));
            return productos;
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron cargar los productos.", e);
        }
    }

    @Override
    public Optional<Producto> findByBarcode(String codigoBarras) {
        String sql = baseSelect() + " WHERE p.activo = 1 AND p.codigo_barras = ? LIMIT 1";
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, codigoBarras);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo buscar el producto por código de barras.", e);
        }
    }

    @Override
    public Optional<Producto> findByPlu(int plu) {
        String sql = baseSelect() + " WHERE p.activo = 1 AND p.plu_balanza = ? LIMIT 1";
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, plu);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo buscar el producto por PLU.", e);
        }
    }

    @Override
    public Producto create(Producto producto) {
        String sql = """
                INSERT INTO producto
                    (nombre, categoria_id, unidad_medida, precio_venta, costo, stock_actual, activo, codigo_barras, plu_balanza)
                VALUES (?, ?, ?, ?, ?, 0, 1, ?, ?)
                """;
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindEditableFields(statement, producto);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Producto(keys.getLong(1), producto.nombre(), producto.categoriaId(),
                            producto.categoriaNombre(), producto.unidadMedida(), producto.precioVenta(),
                            producto.costo(), 0d, true, producto.codigoBarras(), producto.pluBalanza());
                }
            }
            throw new IllegalStateException("SQLite no devolvió el id del producto creado.");
        } catch (Exception e) {
            throw translateWriteError("No se pudo crear el producto.", e);
        }
    }

    @Override
    public Producto update(Producto producto) {
        String sql = """
                UPDATE producto
                SET nombre = ?, categoria_id = ?, unidad_medida = ?, precio_venta = ?, costo = ?,
                    codigo_barras = ?, plu_balanza = ?, actualizado_en = datetime('now')
                WHERE id = ? AND activo = 1
                """;
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindEditableFields(statement, producto);
            statement.setLong(8, producto.id());
            int updated = statement.executeUpdate();
            if (updated == 0) throw new IllegalStateException("El producto ya no está disponible para editar.");
            return producto;
        } catch (Exception e) {
            throw translateWriteError("No se pudo actualizar el producto.", e);
        }
    }

    @Override
    public void deactivate(long productoId) {
        String sql = """
                UPDATE producto
                SET activo = 0, actualizado_en = datetime('now')
                WHERE id = ? AND activo = 1
                """;
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productoId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo desactivar el producto.", e);
        }
    }

    private String baseSelect() {
        return """
                SELECT p.id, p.nombre, p.categoria_id, c.nombre AS categoria_nombre,
                       p.unidad_medida, p.precio_venta, p.costo, p.stock_actual, p.activo,
                       p.codigo_barras, p.plu_balanza
                FROM producto p
                LEFT JOIN categoria_producto c ON c.id = p.categoria_id
                """;
    }

    private void bindEditableFields(PreparedStatement statement, Producto producto) throws Exception {
        statement.setString(1, producto.nombre());
        if (producto.categoriaId() == null) statement.setNull(2, java.sql.Types.INTEGER);
        else statement.setLong(2, producto.categoriaId());
        statement.setString(3, producto.unidadMedida().name());
        statement.setDouble(4, producto.precioVenta());
        statement.setDouble(5, producto.costo());
        if (producto.codigoBarras() == null) statement.setNull(6, java.sql.Types.VARCHAR);
        else statement.setString(6, producto.codigoBarras());
        if (producto.pluBalanza() == null) statement.setNull(7, java.sql.Types.INTEGER);
        else statement.setInt(7, producto.pluBalanza());
    }

    private Producto map(ResultSet resultSet) throws Exception {
        long categoriaValue = resultSet.getLong("categoria_id");
        Long categoriaId = resultSet.wasNull() ? null : categoriaValue;
        int pluValue = resultSet.getInt("plu_balanza");
        Integer plu = resultSet.wasNull() ? null : pluValue;
        return new Producto(
                resultSet.getLong("id"), resultSet.getString("nombre"), categoriaId,
                resultSet.getString("categoria_nombre"), UnidadMedida.valueOf(resultSet.getString("unidad_medida")),
                resultSet.getDouble("precio_venta"), resultSet.getDouble("costo"),
                resultSet.getDouble("stock_actual"), resultSet.getInt("activo") == 1,
                resultSet.getString("codigo_barras"), plu
        );
    }

    private RuntimeException translateWriteError(String fallback, Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("codigo_barras")) {
            return new RuntimeException("Ese código de barras ya está asignado a otro producto.");
        }
        if (message.contains("plu_balanza")) {
            return new RuntimeException("Ese PLU de balanza ya está asignado a otro producto.");
        }
        return new RuntimeException(fallback, e);
    }
}
