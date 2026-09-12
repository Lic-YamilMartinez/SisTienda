package py.sistienda.data.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class DbPaths {
    public static final String DATA_DIR_PROPERTY = "sistienda.data.dir";
    public static final String DATA_DIR_ENV = "SISTIENDA_DATA_DIR";

    private DbPaths() {}

    /**
     * Carpeta estable de datos de SisTienda. Queda fuera del directorio de
     * instalación para que una actualización o reinstalación no toque la base.
     */
    public static Path dataDir() {
        String configured = System.getProperty(DATA_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim()).toAbsolutePath().normalize();
        }

        String environment = System.getenv(DATA_DIR_ENV);
        if (environment != null && !environment.isBlank()) {
            return Path.of(environment.trim()).toAbsolutePath().normalize();
        }

        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "SisTienda").toAbsolutePath().normalize();
            }
        }

        return Path.of(System.getProperty("user.home"), ".sistienda")
                .toAbsolutePath()
                .normalize();
    }

    public static Path databaseFile() {
        return dataDir().resolve("sistienda.db");
    }

    public static Path backupDir() {
        return dataDir().resolve("backups");
    }

    /**
     * Ubicación utilizada durante los primeros sprints. Se conserva únicamente
     * para migrar instalaciones de desarrollo/piloto sin perder información.
     */
    public static Path legacyDevDataDir() {
        return Path.of(System.getProperty("user.home"), ".sistienda", "dev")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Migra de forma no destructiva la base y los backups de la ubicación
     * histórica ~/.sistienda/dev a la carpeta estable actual. Nunca reemplaza
     * una base o backup que ya exista en el destino.
     */
    public static void migrateLegacyDataIfNeeded(Path targetDatabase) {
        Path expected = databaseFile().toAbsolutePath().normalize();
        Path target = targetDatabase.toAbsolutePath().normalize();
        if (!expected.equals(target)) {
            return;
        }
        migrateLegacyDataIfNeeded(target, legacyDevDataDir());
    }

    static void migrateLegacyDataIfNeeded(Path targetDatabase, Path legacyDataDir) {
        Path target = targetDatabase.toAbsolutePath().normalize();
        Path legacy = legacyDataDir.toAbsolutePath().normalize();
        Path legacyDatabase = legacy.resolve("sistienda.db");
        Path targetBackups = target.getParent().resolve("backups");
        Path legacyBackups = legacy.resolve("backups");

        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target) && Files.isRegularFile(legacyDatabase)) {
                Files.copy(legacyDatabase, target, StandardCopyOption.COPY_ATTRIBUTES);
            }

            if (Files.isDirectory(legacyBackups)) {
                Files.createDirectories(targetBackups);
                try (var files = Files.list(legacyBackups)) {
                    for (Path source : files.filter(Files::isRegularFile).toList()) {
                        Path destination = targetBackups.resolve(source.getFileName());
                        if (!Files.exists(destination)) {
                            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron migrar los datos anteriores de SisTienda.", e);
        }
    }

    /** Compatibilidad temporal con código de sprints anteriores. */
    public static Path devDataDir() {
        return dataDir();
    }

    /** Compatibilidad temporal con código de sprints anteriores. */
    public static Path devDbFile() {
        return databaseFile();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
