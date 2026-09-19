package py.sistienda.ui.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class AppLog {

    private static final Logger LOGGER = Logger.getLogger("py.sistienda");
    private static volatile boolean initialized;

    private AppLog() {
    }

    public static synchronized void init(Path dataDir) {
        if (initialized) return;
        try {
            Path logDir = dataDir.resolve("logs");
            Files.createDirectories(logDir);
            FileHandler handler = new FileHandler(
                    logDir.resolve("sistienda-%g.log").toString(),
                    2 * 1024 * 1024,
                    5,
                    true
            );
            handler.setFormatter(new SimpleFormatter());
            LOGGER.setUseParentHandlers(false);
            LOGGER.addHandler(handler);
            LOGGER.setLevel(Level.INFO);
            initialized = true;
            info("Registro de soporte inicializado.");
        } catch (IOException e) {
            System.err.println("SisTienda no pudo inicializar el log local: " + e.getMessage());
        }
    }

    public static void info(String message) {
        LOGGER.log(Level.INFO, message);
    }

    public static void error(String message, Throwable error) {
        LOGGER.log(Level.SEVERE, message, error);
    }
}
