package py.sistienda.data.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseInfrastructureTest {

    @Test
    void devDbFile_seConstruyeDentroDelDirectorioDev() {
        Path esperado = DbPaths.devDataDir().resolve("sistienda.db");

        assertEquals(esperado, DbPaths.devDbFile());
    }

    @Test
    void jdbcUrl_tienePrefijoSqliteYApuntaAlArchivoDev() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory();

        assertTrue(factory.jdbcUrl().startsWith("jdbc:sqlite:"));
        assertTrue(factory.jdbcUrl().endsWith(DbPaths.devDbFile().toAbsolutePath().toString()));
    }

    @Test
    void esquemaInicial_estaDisponibleComoRecurso() {
        assertNotNull(DatabaseInitializer.class.getResource("/db/V1__init.sql"));
    }
}
