package py.sistienda.data.database;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DatabaseInitializer {

    private final SqliteConnectionFactory connectionFactory;

    public DatabaseInitializer(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public void initialize() {
        try {
            var parent = connectionFactory.databaseFile().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Connection connection = connectionFactory.open()) {
                connection.setAutoCommit(false);

                try {
                    String sql = readResource("/db/V1__init.sql");
                    runSqlScriptSqlite(connection.createStatement(), sql);
                    connection.commit();
                } catch (Exception e) {
                    try {
                        connection.rollback();
                    } catch (Exception rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando la base de datos", e);
        }
    }

    private String readResource(String path) throws Exception {
        try (var inputStream = DatabaseInitializer.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("No se encontró el recurso: " + path);
            }

            try (var reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    private void runSqlScriptSqlite(Statement statement, String sql) throws Exception {
        String normalized = sql
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        StringBuilder buffer = new StringBuilder();
        boolean inTrigger = false;
        int statementIndex = 0;

        try (statement) {
            for (String rawLine : normalized.split("\n")) {
                String line = rawLine.trim();

                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }

                if (!inTrigger && line.toUpperCase().startsWith("CREATE TRIGGER")) {
                    inTrigger = true;
                }

                buffer.append(rawLine).append("\n");

                if (inTrigger) {
                    if (line.equalsIgnoreCase("END;")) {
                        statementIndex++;
                        execute(statement, buffer.toString(), statementIndex);
                        buffer.setLength(0);
                        inTrigger = false;
                    }
                } else if (line.endsWith(";")) {
                    statementIndex++;
                    execute(statement, buffer.toString(), statementIndex);
                    buffer.setLength(0);
                }
            }

            String rest = buffer.toString().trim();
            if (!rest.isEmpty()) {
                statementIndex++;
                execute(statement, rest, statementIndex);
            }
        }
    }

    private void execute(Statement statement, String sql, int index) {
        String statementSql = sql.trim();
        if (statementSql.isEmpty()) {
            return;
        }

        try {
            statement.execute(statementSql);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error en sentencia #" + index + ":\n" + statementSql,
                    e
            );
        }
    }
}
