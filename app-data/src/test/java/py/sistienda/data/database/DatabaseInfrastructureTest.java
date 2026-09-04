package py.sistienda.data.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseInfrastructureTest {

    @TempDir
    Path tempDir;

    @Test
    void devDbFile_seConstruyeDentroDelDirectorioDev() {
        Path esperado = DbPaths.devDataDir().resolve("sistienda.db");

        assertEquals(esperado, DbPaths.devDbFile());
    }

    @Test
    void jdbcUrl_tienePrefijoSqliteYApuntaAlArchivoConfigurado() {
        Path dbFile = tempDir.resolve("custom.db");
        SqliteConnectionFactory factory = new SqliteConnectionFactory(dbFile);

        assertTrue(factory.jdbcUrl().startsWith("jdbc:sqlite:"));
        assertEquals(dbFile.toAbsolutePath(), factory.databaseFile());
    }

    @Test
    void esquemaInicial_estaDisponibleComoRecurso() {
        assertNotNull(DatabaseInitializer.class.getResource("/db/V1__init.sql"));
    }

    @Test
    void initialize_creaEsquemaYActivaForeignKeys() throws Exception {
        Path dbFile = tempDir.resolve("sistienda-test.db");
        SqliteConnectionFactory factory = new SqliteConnectionFactory(dbFile);
        DatabaseInitializer initializer = new DatabaseInitializer(factory);

        initializer.initialize();

        try (var connection = factory.open();
             var pragma = connection.createStatement().executeQuery("PRAGMA foreign_keys")) {
            assertTrue(pragma.next());
            assertEquals(1, pragma.getInt(1));
        }

        try (var connection = factory.open();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'producto'")) {
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void triggerStock_rechazaSalidaConStockInsuficiente() throws Exception {
        Path dbFile = tempDir.resolve("stock-test.db");
        SqliteConnectionFactory factory = new SqliteConnectionFactory(dbFile);
        new DatabaseInitializer(factory).initialize();

        try (var connection = factory.open()) {
            connection.createStatement().executeUpdate("""
                    INSERT INTO producto (nombre, unidad_medida, precio_venta, costo, stock_actual)
                    VALUES ('Producto test', 'UN', 1000, 500, 0)
                    """);

            SQLException error = assertThrows(SQLException.class, () ->
                    connection.createStatement().executeUpdate("""
                            INSERT INTO mov_stock (producto_id, tipo, motivo, cantidad)
                            VALUES (1, 'SALIDA', 'TEST', 1)
                            """)
            );

            assertTrue(error.getMessage().contains("Stock insuficiente"));
        }
    }
}
