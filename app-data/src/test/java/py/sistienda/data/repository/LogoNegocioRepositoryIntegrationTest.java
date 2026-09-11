package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.LogoNegocio;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoNegocioRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void guardaCargaYEliminaLogoDentroDeLaBase() {
        Path database = tempDir.resolve("branding.db");
        SqliteConnectionFactory factory = new SqliteConnectionFactory(database);
        new DatabaseInitializer(factory).initialize();
        SqliteLogoNegocioRepository repository = new SqliteLogoNegocioRepository(factory);
        byte[] content = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3, 4};

        repository.save(new LogoNegocio(content, "image/jpeg"));

        var loaded = new SqliteLogoNegocioRepository(new SqliteConnectionFactory(database)).get().orElseThrow();
        assertEquals("image/jpeg", loaded.mimeType());
        assertArrayEquals(content, loaded.contenido());

        repository.delete();
        assertTrue(repository.get().isEmpty());
    }
}
