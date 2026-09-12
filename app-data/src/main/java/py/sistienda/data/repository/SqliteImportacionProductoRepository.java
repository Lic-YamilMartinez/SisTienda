package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionProductoEntrada;
import py.sistienda.core.model.ImportacionProductoResultado;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.ImportacionProductoRepository;
import py.sistienda.core.service.CodigoBarrasService;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SqliteImportacionProductoRepository implements ImportacionProductoRepository {
    private final SqliteConnectionFactory connectionFactory;
    private final CodigoBarrasService codigoBarrasService = new CodigoBarrasService();

    public SqliteImportacionProductoRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public Set<String> codigosExistentes(List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) return Set.of();
        Set<String> buscados = new HashSet<>();
        codigos.forEach(value -> {
            if (value != null) buscados.add(value.toLowerCase(Locale.ROOT));
        });
        Set<String> encontrados = new HashSet<>();
        String sql = "SELECT codigo_barras FROM producto WHERE codigo_barras IS NOT NULL AND trim(codigo_barras) <> ''";
        try (var connection = connectionFactory.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            while (result.next()) {
                String codigo = result.getString(1);
                if (buscados.contains(codigo.toLowerCase(Locale.ROOT))) encontrados.add(codigo);
            }
            return Set.copyOf(encontrados);
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron validar los códigos existentes.", e);
        }
    }

    @Override
    public Set<Integer> plusExistentes(List<Integer> plus) {
        if (plus == null || plus.isEmpty()) return Set.of();
        Set<Integer> buscados = new HashSet<>(plus);
        Set<Integer> encontrados = new HashSet<>();
        String sql = "SELECT plu_balanza FROM producto WHERE plu_balanza IS NOT NULL";
        try (var connection = connectionFactory.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            while (result.next()) {
                int plu = result.getInt(1);
                if (buscados.contains(plu)) encontrados.add(plu);
            }
            return Set.copyOf(encontrados);
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron validar los PLU existentes.", e);
        }
    }

    @Override
    public ImportacionProductoResultado importar(List<ImportacionProductoEntrada> productos, long usuarioId) {
        Objects.requireNonNull(productos);
        if (productos.isEmpty()) throw new ValidationException("No hay productos para importar.");
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Long> categorias = cargarCategorias(connection);
                int categoriasCreadas = 0;
                int movimientos = 0;

                for (ImportacionProductoEntrada producto : productos) {
                    Long categoriaId = null;
                    if (producto.categoria() != null && !producto.categoria().isBlank()) {
                        String key = producto.categoria().trim().toLowerCase(Locale.ROOT);
                        categoriaId = categorias.get(key);
                        if (categoriaId == null) {
                            categoriaId = crearCategoria(connection, producto.categoria().trim());
                            categorias.put(key, categoriaId);
                            categoriasCreadas++;
                        } else {
                            reactivarCategoria(connection, categoriaId);
                        }
                    }

                    long productoId = insertarProducto(connection, producto, categoriaId);
                    completarIdentificacion(connection, productoId, producto);
                    if (producto.stockInicial() > 0.000001d) {
                        registrarStockInicial(connection, productoId, producto.stockInicial(), usuarioId, producto.fila());
                        movimientos++;
                    }
                }

                connection.commit();
                return new ImportacionProductoResultado(productos.size(), categoriasCreadas, movimientos);
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw translate(e);
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo completar la importación de productos.", e);
        }
    }

    private Map<String, Long> cargarCategorias(Connection connection) throws Exception {
        Map<String, Long> result = new HashMap<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, nombre FROM categoria_producto")) {
            while (rows.next()) {
                result.put(rows.getString("nombre").trim().toLowerCase(Locale.ROOT), rows.getLong("id"));
            }
        }
        return result;
    }

    private long crearCategoria(Connection connection, String nombre) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO categoria_producto (nombre, activo) VALUES (?, 1)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nombre);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("SQLite no devolvió la categoría creada.");
                return keys.getLong(1);
            }
        }
    }

    private void reactivarCategoria(Connection connection, long categoriaId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE categoria_producto SET activo = 1 WHERE id = ? AND activo = 0")) {
            statement.setLong(1, categoriaId);
            statement.executeUpdate();
        }
    }

    private long insertarProducto(Connection connection, ImportacionProductoEntrada producto, Long categoriaId) throws Exception {
        String sql = """
                INSERT INTO producto
                    (nombre, categoria_id, unidad_medida, precio_venta, costo, stock_actual,
                     stock_minimo, stock_ideal, activo, codigo_barras, plu_balanza)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, 1, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, producto.nombre());
            if (categoriaId == null) statement.setNull(2, java.sql.Types.INTEGER);
            else statement.setLong(2, categoriaId);
            statement.setString(3, producto.unidadMedida().name());
            statement.setDouble(4, producto.precioVenta());
            statement.setDouble(5, producto.costo());
            statement.setDouble(6, producto.stockMinimo());
            statement.setDouble(7, producto.stockIdeal());
            if (producto.codigoBarras() == null) statement.setNull(8, java.sql.Types.VARCHAR);
            else statement.setString(8, producto.codigoBarras());
            if (producto.pluBalanza() == null) statement.setNull(9, java.sql.Types.INTEGER);
            else statement.setInt(9, producto.pluBalanza());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("SQLite no devolvió el producto creado.");
                return keys.getLong(1);
            }
        }
    }

    private void completarIdentificacion(Connection connection, long productoId, ImportacionProductoEntrada producto) throws Exception {
        if (producto.unidadMedida() == UnidadMedida.UN && producto.codigoBarras() == null) {
            String codigo = codigoBarrasService.generarCodigoInterno(productoId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE producto SET codigo_barras = ? WHERE id = ?")) {
                statement.setString(1, codigo);
                statement.setLong(2, productoId);
                statement.executeUpdate();
            }
        }
        if (producto.unidadMedida() == UnidadMedida.KG && producto.pluBalanza() == null) {
            if (productoId > 99_999) {
                throw new ValidationException("El producto de la fila " + producto.fila()
                        + " necesita un PLU manual porque el identificador automático supera 99999.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE producto SET plu_balanza = ? WHERE id = ?")) {
                statement.setInt(1, Math.toIntExact(productoId));
                statement.setLong(2, productoId);
                statement.executeUpdate();
            }
        }
    }

    private void registrarStockInicial(Connection connection, long productoId, double cantidad,
                                       long usuarioId, int fila) throws Exception {
        String sql = """
                INSERT INTO mov_stock
                    (producto_id, tipo, motivo, cantidad, referencia, usuario_id, observacion)
                VALUES (?, 'ENTRADA', 'Stock inicial - importación', ?, ?, ?, 'Carga masiva de productos')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productoId);
            statement.setDouble(2, cantidad);
            statement.setString(3, "IMPORTACION-FILA-" + fila);
            statement.setLong(4, usuarioId);
            statement.executeUpdate();
        }
    }

    private RuntimeException translate(Exception error) {
        if (error instanceof ValidationException validation) return validation;
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("codigo_barras")) {
            return new ValidationException("La importación encontró un código de barras que ya está en uso. Volvé a validar el archivo.");
        }
        if (message.contains("plu_balanza")) {
            return new ValidationException("La importación encontró un PLU que ya está en uso. Volvé a validar el archivo.");
        }
        if (message.contains("categoria_producto.nombre")) {
            return new ValidationException("Hay categorías duplicadas que no se pudieron resolver.");
        }
        return new RuntimeException("No se pudo completar la importación. No se guardó ningún producto.", error);
    }
}
