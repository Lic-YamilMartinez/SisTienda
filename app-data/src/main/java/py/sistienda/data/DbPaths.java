package py.sistienda.data;

import java.nio.file.Path;

public final class DbPaths {
    private DbPaths(){}

    public static Path devDataDir() {
        return Path.of(System.getProperty("user.home"), ".sistienda", "dev");
    }

    public static Path devDbFile() {
        return devDataDir().resolve("sistienda.db");
    }
}
