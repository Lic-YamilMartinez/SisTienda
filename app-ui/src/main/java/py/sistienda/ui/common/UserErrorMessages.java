package py.sistienda.ui.common;

import py.sistienda.core.exception.ValidationException;

import java.util.Locale;

public final class UserErrorMessages {

    private UserErrorMessages() {
    }

    public static String message(Throwable error) {
        if (error == null) return "No pudimos completar la operación.";

        Throwable current = error;
        while (current != null) {
            if (current instanceof ValidationException
                    && current.getMessage() != null
                    && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }

        AppLog.error("Error no controlado en una operación de interfaz.", error);

        current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank() && !looksTechnical(message)) {
                return message;
            }
            current = current.getCause();
        }
        return "No pudimos completar la operación. Cerrá y volvé a intentar; si continúa, revisá el log de soporte.";
    }

    private static boolean looksTechnical(String message) {
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("sqlite")
                || value.contains("jdbc:")
                || value.contains("constraint")
                || value.contains("foreign key")
                || value.contains("database is locked")
                || value.contains("stacktrace")
                || value.contains("sqlstate")
                || value.contains("java.sql.")
                || value.contains("org.sqlite.");
    }
}
