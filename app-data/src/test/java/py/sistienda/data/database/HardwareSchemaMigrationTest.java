package py.sistienda.data.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HardwareSchemaMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void agregaCodigoYPluAUnaBaseDeSprintsAnteriores() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        var factory = new SqliteConnectionFactory(database);

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE producto (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      nombre TEXT NOT NULL,
                      categoria_id INTEGER,
                      unidad_medida TEXT NOT NULL CHECK (unidad_medida IN ('UN','KG')),
                      precio_venta REAL NOT NULL CHECK (precio_venta >= 0),
                      costo REAL NOT NULL DEFAULT 0 CHECK (costo >= 0),
                      stock_actual REAL NOT NULL DEFAULT 0,
                      activo INTEGER NOT NULL DEFAULT 1,
                      creado_en TEXT NOT NULL DEFAULT (datetime('now')),
                      actualizado_en TEXT
                    )
                    """);
            statement.execute("INSERT INTO producto (nombre, unidad_medida, precio_venta, costo, stock_actual) VALUES ('Producto legado', 'UN', 1000, 500, 3)");
        }

        new DatabaseInitializer(factory).initialize();

        Set<String> columns = new HashSet<>();
        try (var connection = factory.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(producto)")) {
            while (result.next()) columns.add(result.getString("name"));
        }
        assertTrue(columns.contains("codigo_barras"));
        assertTrue(columns.contains("plu_balanza"));

        try (var connection = factory.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT nombre, stock_actual FROM producto WHERE nombre = 'Producto legado'")) {
            assertTrue(result.next());
            assertEquals("Producto legado", result.getString("nombre"));
            assertEquals(3, result.getDouble("stock_actual"), 0.001);
        }
    }
}
