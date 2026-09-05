package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.Empresa;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmpresaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void cargaYGuardaConfiguracionEnSqliteReal() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("empresa-test.db"));
        new DatabaseInitializer(factory).initialize();

        var repository = new SqliteEmpresaRepository(factory);
        Empresa initial = repository.get();
        assertEquals("Mi Tienda", initial.nombre());
        assertNull(initial.ruc());

        Empresa saved = repository.save(new Empresa(
                1,
                "Tienda Central",
                "80012345-6",
                "Av. Principal 123",
                "0981000000",
                "Gracias por su compra"
        ));

        assertEquals("Tienda Central", saved.nombre());
        assertEquals("80012345-6", saved.ruc());
        assertEquals("Gracias por su compra", repository.get().mensajeTicket());
    }
}
