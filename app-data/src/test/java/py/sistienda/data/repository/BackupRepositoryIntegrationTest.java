package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.Empresa;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void creaSnapshotYRestauracionRecuperaDatosAnteriores() throws Exception {
        Path dbFile = tempDir.resolve("sistienda.db");
        Path backupDir = tempDir.resolve("backups");
        SqliteConnectionFactory factory = new SqliteConnectionFactory(dbFile);
        new DatabaseInitializer(factory).initialize();

        var empresaRepository = new SqliteEmpresaRepository(factory);
        empresaRepository.save(new Empresa(1, "Estado Original", null, null, null, null));

        var backupRepository = new SqliteBackupRepository(factory, backupDir);
        var backup = backupRepository.crearManual();

        assertTrue(Files.isRegularFile(backup.archivo()));
        assertTrue(backup.bytes() > 0);
        assertFalse(backup.automatico());

        empresaRepository.save(new Empresa(1, "Estado Modificado", null, null, null, null));
        assertEquals("Estado Modificado", empresaRepository.get().nombre());

        backupRepository.restaurar(backup.archivo());

        assertEquals("Estado Original", empresaRepository.get().nombre());
        assertTrue(backupRepository.listar().stream()
                .anyMatch(info -> info.nombreArchivo().startsWith("pre-restore-")));
    }

    @Test
    void automaticoNoDuplicaElArchivoDelMismoDia() {
        Path dbFile = tempDir.resolve("auto.db");
        Path backupDir = tempDir.resolve("auto-backups");
        SqliteConnectionFactory factory = new SqliteConnectionFactory(dbFile);
        new DatabaseInitializer(factory).initialize();

        var repository = new SqliteBackupRepository(factory, backupDir);
        var first = repository.crearAutomatico();
        var second = repository.crearAutomatico();

        assertEquals(first.archivo(), second.archivo());
        assertEquals(1L, repository.listar().stream().filter(info -> info.automatico()).count());
    }
}
