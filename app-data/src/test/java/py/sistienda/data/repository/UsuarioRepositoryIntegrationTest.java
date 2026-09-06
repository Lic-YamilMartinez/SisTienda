package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.RolUsuario;
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

    @Test
    void administraRolesEstadoYPasswordSinPerderUsuarios() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("usuarios-crud.db"));
        new DatabaseInitializer(factory).initialize();
        SqliteUsuarioRepository repository = new SqliteUsuarioRepository(factory);

        var dueno = repository.createOwner("dueno", "hash-dueno");
        var cajero = repository.create("caja01", "hash-caja", RolUsuario.CAJERO);
        var vendedor = repository.create("venta01", "hash-venta", RolUsuario.VENDEDOR);

        assertEquals(3, repository.findAll().size());
        assertEquals(1, repository.countActiveByRole(RolUsuario.DUENIO));
        assertEquals(RolUsuario.CAJERO, repository.findById(cajero.id()).orElseThrow().rolUsuario());

        var promovido = repository.updateRole(vendedor.id(), RolUsuario.ADMINISTRADOR);
        assertEquals(RolUsuario.ADMINISTRADOR, promovido.rolUsuario());

        var desactivado = repository.setActive(cajero.id(), false);
        assertFalse(desactivado.activo());
        assertTrue(repository.findActiveByUsername("caja01").isEmpty());

        repository.updatePassword(dueno.id(), "hash-nuevo");
        assertEquals("hash-nuevo", repository.findActiveByUsername("dueno").orElseThrow().passwordHash());
    }
}
