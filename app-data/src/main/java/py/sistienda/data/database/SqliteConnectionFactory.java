package py.sistienda.data.database;

import py.sistienda.data.DbPaths;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteConnectionFactory {

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl());
        enableForeignKeys(connection);
        return connection;
    }

    public String jdbcUrl() {
        return "jdbc:sqlite:" + DbPaths.devDbFile().toAbsolutePath();
    }

    private void enableForeignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }
}
