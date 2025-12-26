package py.sistienda.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;

public final class Database {

    private Database() {}

    /* =========================
       JDBC
       ========================= */
    public static String jdbcUrlDev() {
        return "jdbc:sqlite:" + DbPaths.devDbFile().toAbsolutePath();
    }

    /* =========================
       INIT
       ========================= */
    public static void initDev() {
        try {
            Files.createDirectories(DbPaths.devDataDir());

            try (Connection con = DriverManager.getConnection(jdbcUrlDev())) {
                con.setAutoCommit(false);

                String sql = readResource("/db/V1__init.sql");
                runSqlScriptSqlite(con, sql);

                con.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando la base de datos", e);
        }
    }

    /* =========================
       RESOURCE READER
       ========================= */
    private static String readResource(String path) throws Exception {
        try (var is = Database.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("No se encontró el recurso: " + path);
            }

            try (var br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /* =========================
       SQLITE SCRIPT RUNNER
       ========================= */
    private static void runSqlScriptSqlite(Connection con, String sql) throws Exception {

        // Normalizar saltos de línea (Linux / Windows)
        String normalized = sql
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        StringBuilder buffer = new StringBuilder();
        boolean inTrigger = false;
        int stmtIndex = 0;

        try (Statement st = con.createStatement()) {

            for (String rawLine : normalized.split("\n")) {
                String line = rawLine.trim();

                // ignorar vacíos y comentarios
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }

                // detectar inicio de trigger
                if (!inTrigger && line.toUpperCase().startsWith("CREATE TRIGGER")) {
                    inTrigger = true;
                }

                buffer.append(rawLine).append("\n");

                if (inTrigger) {
                    // trigger termina SOLO con END;
                    if (line.equalsIgnoreCase("END;")) {
                        stmtIndex++;
                        execute(st, buffer.toString(), stmtIndex);
                        buffer.setLength(0);
                        inTrigger = false;
                    }
                } else {
                    // sentencias normales terminan en ;
                    if (line.endsWith(";")) {
                        stmtIndex++;
                        execute(st, buffer.toString(), stmtIndex);
                        buffer.setLength(0);
                    }
                }
            }

            // seguridad: ejecutar resto si quedó algo
            String rest = buffer.toString().trim();
            if (!rest.isEmpty()) {
                stmtIndex++;
                execute(st, rest, stmtIndex);
            }
        }
    }

    /* =========================
       EXECUTOR
       ========================= */
    private static void execute(Statement st, String sql, int idx) {
        String s = sql.trim();
        if (s.isEmpty()) return;

        try {
            st.execute(s);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error en sentencia #" + idx + ":\n" + s,
                    e
            );
        }
    }
}
