package py.sistienda.ui.common;

import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {

    private static final String VERSION = load();

    private AppVersion() {
    }

    public static String current() {
        return VERSION;
    }

    private static String load() {
        try (InputStream input = AppVersion.class.getResourceAsStream("/app-version.properties")) {
            if (input == null) return "dev";
            Properties properties = new Properties();
            properties.load(input);
            String value = properties.getProperty("version");
            return value == null || value.isBlank() ? "dev" : value.trim();
        } catch (Exception e) {
            return "dev";
        }
    }
}
