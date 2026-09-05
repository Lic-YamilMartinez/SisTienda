package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void creaYRecuperaUsuarioDuenoEnSqliteReal() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("usuarios-test.db"));
        new DatabaseInitializer(factory).initialize();

        SqliteUsuarioRepository repository = new SqliteUsuarioRepository(factory);
        assertFalse(repository.existsActiveUser());

        var creado = repository.createOwner("admin", "hash-de-prueba");
        assertTrue(creado.id() > 0);
        assertEquals("DUENIO", creado.rol());
        assertTrue(repository.existsActiveUser());

        var encontrado = repository.findActiveByUsername("admin").orElseThrow();
        assertEquals(creado.id(), encontrado.id());
        assertEquals("hash-de-prueba", encontrado.passwordHash());
    }
}
