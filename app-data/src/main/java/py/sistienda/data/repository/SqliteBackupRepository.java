package py.sistienda.data.repository;

import py.sistienda.core.model.BackupInfo;
import py.sistienda.core.repository.BackupRepository;
import py.sistienda.data.database.DbPaths;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class SqliteBackupRepository implements BackupRepository {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SqliteConnectionFactory connectionFactory;
    private final Path backupDirectory;

    public SqliteBackupRepository(SqliteConnectionFactory connectionFactory) {
        this(connectionFactory, DbPaths.backupDir());
    }

    public SqliteBackupRepository(SqliteConnectionFactory connectionFactory, Path backupDirectory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.backupDirectory = Objects.requireNonNull(backupDirectory).toAbsolutePath();
    }

    @Override
    public BackupInfo crearManual() {
        return crearSnapshot("manual-" + TIMESTAMP.format(LocalDateTime.now()) + ".db");
    }

    @Override
    public BackupInfo crearAutomatico() {
        String filename = "auto-" + DAY.format(LocalDateTime.now()) + ".db";
        Path target = backupDirectory.resolve(filename);
        if (Files.exists(target)) {
            return info(target);
        }
        return crearSnapshot(filename);
    }

    @Override
    public List<BackupInfo> listar() {
        try {
            Files.createDirectories(backupDirectory);
            try (Stream<Path> files = Files.list(backupDirectory)) {
                return files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".db"))
                        .map(this::info)
                        .sorted(Comparator.comparing(BackupInfo::creadoEn).reversed())
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron listar los backups.", e);
        }
    }

    @Override
    public void restaurar(Path backupFile) {
        Path source = backupFile.toAbsolutePath().normalize();
        validarIntegridad(source);

        crearSnapshot("pre-restore-" + TIMESTAMP.format(LocalDateTime.now()) + ".db");

        Path database = databaseFile().toAbsolutePath();
        Path temp = database.resolveSibling(database.getFileName() + ".restore.tmp");
        try {
            Files.createDirectories(database.getParent());
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            validarIntegridad(temp);
            try {
                Files.move(temp, database,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, database, StandardCopyOption.REPLACE_EXISTING);
            }
            validarIntegridad(database);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo restaurar el backup.", e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Limpieza best-effort del archivo temporal.
            }
        }
    }

    @Override
    public Path databaseFile() {
        return connectionFactory.databaseFile();
    }

    @Override
    public Path backupDirectory() {
        return backupDirectory;
    }

    private BackupInfo crearSnapshot(String filename) {
        try {
            Files.createDirectories(backupDirectory);
            Path target = backupDirectory.resolve(filename).toAbsolutePath();
            Files.deleteIfExists(target);

            try (var connection = connectionFactory.open();
                 var statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, target.toString());
                statement.execute();
            }

            validarIntegridad(target);
            return info(target);
        } catch (IOException | SQLException e) {
            throw new RuntimeException("No se pudo crear el backup de SisTienda.", e);
        }
    }

    private void validarIntegridad(Path database) {
        if (!Files.isRegularFile(database)) {
            throw new RuntimeException("El archivo de backup no existe.");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new RuntimeException("El backup no supera la verificación de integridad.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo validar la integridad del backup.", e);
        }
    }

    private BackupInfo info(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            Instant modified = attrs.lastModifiedTime().toInstant();
            LocalDateTime createdAt = LocalDateTime.ofInstant(modified, ZoneId.systemDefault());
            String name = path.getFileName().toString();
            return new BackupInfo(
                    path.toAbsolutePath(),
                    attrs.size(),
                    createdAt,
                    name.startsWith("auto-")
            );
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer la información del backup.", e);
        }
    }
}
