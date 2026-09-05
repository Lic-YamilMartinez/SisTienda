package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CajaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void abreConsultaYCierraCajaEnSqliteReal() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("caja-test.db"));
        new DatabaseInitializer(factory).initialize();

        var usuarioRepository = new SqliteUsuarioRepository(factory);
        var usuario = usuarioRepository.createOwner("admin", "hash");
        var repository = new SqliteCajaRepository(factory);

        assertFalse(repository.findOpenByUser(usuario.id()).isPresent());

        var abierta = repository.open(usuario.id(), 150000, "Inicio");
        assertTrue(abierta.abierta());
        assertEquals(150000d, abierta.montoApertura());
        assertTrue(repository.findOpenByUser(usuario.id()).isPresent());

        var cerrada = repository.close(abierta.id(), 250000, "Cierre");
        assertEquals(EstadoCaja.CERRADA, cerrada.estado());
        assertEquals(250000d, cerrada.montoCierre());
        assertFalse(repository.findOpenByUser(usuario.id()).isPresent());
    }
}
