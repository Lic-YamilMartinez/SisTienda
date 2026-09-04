package py.sistienda.data.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class SqliteConnectionFactory {

    private final Path databaseFile;

    public SqliteConnectionFactory() {
        this(DbPaths.devDbFile());
    }

    public SqliteConnectionFactory(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile).toAbsolutePath();
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl());
        try {
            enableForeignKeys(connection);
            return connection;
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public String jdbcUrl() {
        return "jdbc:sqlite:" + databaseFile;
    }

    public Path databaseFile() {
        return databaseFile;
    }

    private void enableForeignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }
}
