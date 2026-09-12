package py.sistienda.data.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbPathsTest {

    @TempDir
    Path tempDir;

    private final String originalDataDir = System.getProperty(DbPaths.DATA_DIR_PROPERTY);

    @AfterEach
    void restoreProperty() {
        if (originalDataDir == null) {
            System.clearProperty(DbPaths.DATA_DIR_PROPERTY);
        } else {
            System.setProperty(DbPaths.DATA_DIR_PROPERTY, originalDataDir);
        }
    }

    @Test
    void permiteConfigurarDirectorioDeDatosParaPilotosYSoporte() {
        Path configured = tempDir.resolve("cliente-01");
        System.setProperty(DbPaths.DATA_DIR_PROPERTY, configured.toString());

        assertEquals(configured.toAbsolutePath().normalize(), DbPaths.dataDir());
        assertEquals(configured.resolve("sistienda.db").toAbsolutePath().normalize(), DbPaths.databaseFile());
        assertEquals(configured.resolve("backups").toAbsolutePath().normalize(), DbPaths.backupDir());
    }

    @Test
    void migraBaseYBackupsAnterioresSinSobrescribirDatosActuales() throws Exception {
        Path legacy = tempDir.resolve("legacy");
        Path legacyBackups = legacy.resolve("backups");
        Files.createDirectories(legacyBackups);
        Files.write(legacy.resolve("sistienda.db"), new byte[]{1, 2, 3});
        Files.write(legacyBackups.resolve("auto-20260911.db"), new byte[]{4, 5, 6});

        Path target = tempDir.resolve("stable").resolve("sistienda.db");
        DbPaths.migrateLegacyDataIfNeeded(target, legacy);

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target));
        Path migratedBackup = target.getParent().resolve("backups").resolve("auto-20260911.db");
        assertTrue(Files.isRegularFile(migratedBackup));
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(migratedBackup));

        Files.write(target, new byte[]{9, 9});
        Files.write(migratedBackup, new byte[]{8, 8});
        Files.write(legacy.resolve("sistienda.db"), new byte[]{7, 7});
        Files.write(legacyBackups.resolve("auto-20260911.db"), new byte[]{6, 6});

        DbPaths.migrateLegacyDataIfNeeded(target, legacy);

        assertArrayEquals(new byte[]{9, 9}, Files.readAllBytes(target));
        assertArrayEquals(new byte[]{8, 8}, Files.readAllBytes(migratedBackup));
    }
}
