package py.sistienda.data.qa;

import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinimercadoDemoSeeder {

    private static final String STOCK_REFERENCE_PREFIX = "QA-MINIMERCADO-";

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct("Bebidas", "Coca-Cola 2 L", "UN", 15_000, 11_000, 18, 6, 24, "QA1001", null),
            new DemoProduct("Bebidas", "Coca-Cola 500 ml", "UN", 7_000, 4_800, 30, 10, 40, "QA1002", null),
            new DemoProduct("Bebidas", "Fanta Naranja 2 L", "UN", 14_000, 10_500, 4, 6, 18, "QA1003", null),
            new DemoProduct("Bebidas", "Agua mineral 500 ml", "UN", 4_000, 2_200, 0, 12, 48, "QA1004", null),
            new DemoProduct("Bebidas", "Jugo de naranja 1 L", "UN", 12_000, 8_500, 10, 4, 16, "QA1005", null),

            new DemoProduct("Lácteos", "Leche entera 1 L", "UN", 9_000, 7_000, 12, 6, 24, "QA1101", null),
            new DemoProduct("Lácteos", "Yogur frutilla 1 L", "UN", 16_000, 12_000, 5, 6, 18, "QA1102", null),
            new DemoProduct("Lácteos", "Manteca 200 g", "UN", 13_000, 9_500, 7, 3, 12, "QA1103", null),
            new DemoProduct("Lácteos", "Queso mozzarella 500 g", "UN", 30_000, 24_000, 4, 3, 10, "QA1104", null),

            new DemoProduct("Alimentos", "Arroz 1 kg", "UN", 10_000, 7_500, 20, 8, 30, "QA1201", null),
            new DemoProduct("Alimentos", "Azúcar 1 kg", "UN", 9_000, 6_500, 15, 6, 24, "QA1202", null),
            new DemoProduct("Alimentos", "Harina de trigo 1 kg", "UN", 8_500, 6_000, 3, 5, 20, "QA1203", null),
            new DemoProduct("Alimentos", "Fideo 400 g", "UN", 6_000, 4_200, 20, 8, 30, "QA1204", null),
            new DemoProduct("Alimentos", "Aceite vegetal 900 ml", "UN", 17_000, 13_500, 8, 4, 16, "QA1205", null),
            new DemoProduct("Alimentos", "Sal fina 1 kg", "UN", 5_000, 3_200, 12, 4, 20, "QA1206", null),
            new DemoProduct("Alimentos", "Yerba mate 500 g", "UN", 14_000, 10_500, 8, 3, 12, "QA1207", null),

            new DemoProduct("Panificados", "Pan lactal 600 g", "UN", 15_000, 11_000, 5, 4, 12, "QA1301", null),
            new DemoProduct("Panificados", "Galletita cracker 400 g", "UN", 10_000, 7_200, 12, 5, 20, "QA1302", null),

            new DemoProduct("Snacks", "Papas fritas 150 g", "UN", 10_000, 6_500, 9, 4, 16, "QA1401", null),
            new DemoProduct("Snacks", "Chocolate 100 g", "UN", 9_000, 5_500, 8, 3, 15, "QA1402", null),
            new DemoProduct("Snacks", "Caramelos surtidos 250 g", "UN", 8_000, 5_000, 10, 4, 18, "QA1403", null),

            new DemoProduct("Higiene", "Jabón de tocador", "UN", 4_500, 2_800, 15, 5, 20, "QA1501", null),
            new DemoProduct("Higiene", "Papel higiénico 4 unidades", "UN", 12_000, 8_500, 6, 4, 16, "QA1502", null),
            new DemoProduct("Higiene", "Pasta dental 90 g", "UN", 10_000, 7_200, 3, 4, 12, "QA1503", null),
            new DemoProduct("Higiene", "Shampoo 400 ml", "UN", 22_000, 16_000, 7, 3, 10, "QA1504", null),

            new DemoProduct("Limpieza", "Detergente 500 ml", "UN", 7_000, 4_800, 14, 5, 20, "QA1601", null),
            new DemoProduct("Limpieza", "Lavandina 1 L", "UN", 6_000, 4_000, 2, 4, 15, "QA1602", null),
            new DemoProduct("Limpieza", "Jabón en polvo 800 g", "UN", 18_000, 13_000, 8, 4, 12, "QA1603", null),
            new DemoProduct("Limpieza", "Esponja de cocina", "UN", 4_500, 2_600, 12, 4, 18, "QA1604", null),

            new DemoProduct("Congelados", "Hamburguesas 4 unidades", "UN", 25_000, 19_000, 4, 2, 8, "QA1701", null),
            new DemoProduct("Congelados", "Hielo 3 kg", "UN", 8_000, 4_500, 10, 3, 15, "QA1702", null),

            new DemoProduct("Frutas y verduras", "Tomate por kg", "KG", 12_000, 8_000, 8.5, 3, 15, null, 91_001),
            new DemoProduct("Frutas y verduras", "Cebolla por kg", "KG", 9_000, 6_000, 12.3, 4, 20, null, 91_002),
            new DemoProduct("Frutas y verduras", "Papa por kg", "KG", 8_000, 5_000, 2.2, 5, 25, null, 91_003),
            new DemoProduct("Frutas y verduras", "Banana por kg", "KG", 10_000, 6_500, 6.4, 3, 15, null, 91_004),
            new DemoProduct("Frutas y verduras", "Manzana por kg", "KG", 18_000, 13_000, 4.7, 3, 12, null, 91_005),
            new DemoProduct("Frutas y verduras", "Naranja por kg", "KG", 9_000, 5_500, 0, 4, 20, null, 91_006),
            new DemoProduct("Granel", "Maní salado por kg", "KG", 30_000, 22_000, 3.2, 1, 8, null, 91_007)
    );

    private MinimercadoDemoSeeder() {
    }

    public static void main(String[] args) {
        SqliteConnectionFactory factory = new SqliteConnectionFactory();
        SeedResult result = seed(factory);
        System.out.println("SisTienda QA · minimercado demo listo.");
        System.out.println("Base: " + factory.databaseFile());
        System.out.println("Productos creados: " + result.created());
        System.out.println("Productos ya existentes: " + result.existing());
        System.out.println("Stocks iniciales cargados: " + result.stockSeeded());
        System.out.println("Total catálogo demo esperado: " + PRODUCTS.size());
    }

    public static SeedResult seed(SqliteConnectionFactory factory) {
        new DatabaseInitializer(factory).initialize();

        try (Connection connection = factory.open()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Long> categories = ensureCategories(connection);
                int created = 0;
                int existing = 0;
                int stockSeeded = 0;

                for (DemoProduct product : PRODUCTS) {
                    Long productId = findProductId(connection, product);
                    if (productId == null) {
                        productId = insertProduct(connection, categories.get(product.category()), product);
                        created++;
                    } else {
                        existing++;
                    }
                    if (seedInitialStock(connection, productId, product)) stockSeeded++;
                }

                connection.commit();
                return new SeedResult(created, existing, stockSeeded, PRODUCTS.size());
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron cargar los datos demo del minimercado.", e);
        }
    }

    private static Map<String, Long> ensureCategories(Connection connection) throws Exception {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String category : PRODUCTS.stream().map(DemoProduct::category).distinct().toList()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO categoria_producto (nombre, activo) VALUES (?, 1)")) {
                insert.setString(1, category);
                insert.executeUpdate();
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id FROM categoria_producto WHERE nombre = ? COLLATE NOCASE LIMIT 1")) {
                query.setString(1, category);
                try (ResultSet rs = query.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("No se pudo resolver la categoría " + category);
                    result.put(category, rs.getLong(1));
                }
            }
        }
        return result;
    }

    private static Long findProductId(Connection connection, DemoProduct product) throws Exception {
        String sql = product.barcode() != null
                ? "SELECT id FROM producto WHERE codigo_barras = ? LIMIT 1"
                : "SELECT id FROM producto WHERE plu_balanza = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (product.barcode() != null) statement.setString(1, product.barcode());
            else statement.setInt(1, product.plu());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : null;
            }
        }
    }

    private static long insertProduct(Connection connection, long categoryId, DemoProduct product) throws Exception {
        String sql = """
                INSERT INTO producto (
                    nombre, categoria_id, unidad_medida, precio_venta, costo, stock_actual,
                    stock_minimo, stock_ideal, activo, codigo_barras, plu_balanza
                ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, 1, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, product.name());
            statement.setLong(2, categoryId);
            statement.setString(3, product.unit());
            statement.setDouble(4, product.salePrice());
            statement.setDouble(5, product.cost());
            statement.setDouble(6, product.minimumStock());
            statement.setDouble(7, product.idealStock());
            if (product.barcode() == null) statement.setNull(8, java.sql.Types.VARCHAR);
            else statement.setString(8, product.barcode());
            if (product.plu() == null) statement.setNull(9, java.sql.Types.INTEGER);
            else statement.setInt(9, product.plu());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("SQLite no devolvió el ID del producto demo.");
                return keys.getLong(1);
            }
        }
    }

    private static boolean seedInitialStock(Connection connection, long productId, DemoProduct product) throws Exception {
        if (product.initialStock() <= 0) return false;
        String reference = STOCK_REFERENCE_PREFIX + product.seedKey();

        try (PreparedStatement exists = connection.prepareStatement(
                "SELECT 1 FROM mov_stock WHERE referencia = ? LIMIT 1")) {
            exists.setString(1, reference);
            try (ResultSet result = exists.executeQuery()) {
                if (result.next()) return false;
            }
        }

        try (PreparedStatement movement = connection.prepareStatement("""
                INSERT INTO mov_stock (
                    producto_id, tipo, motivo, cantidad, referencia, usuario_id, observacion
                ) VALUES (?, 'ENTRADA', 'STOCK_INICIAL', ?, ?, NULL, 'Carga demo de minimercado para QA')
                """)) {
            movement.setLong(1, productId);
            movement.setDouble(2, product.initialStock());
            movement.setString(3, reference);
            movement.executeUpdate();
        }
        return true;
    }

    public record SeedResult(int created, int existing, int stockSeeded, int totalProducts) {
    }

    private record DemoProduct(
            String category,
            String name,
            String unit,
            double salePrice,
            double cost,
            double initialStock,
            double minimumStock,
            double idealStock,
            String barcode,
            Integer plu
    ) {
        String seedKey() {
            return barcode != null ? barcode : "PLU-" + plu;
        }
    }
}
