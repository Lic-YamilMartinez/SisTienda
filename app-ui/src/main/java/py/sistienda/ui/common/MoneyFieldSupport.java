package py.sistienda.ui.common;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formatea montos enteros en guaraníes mientras el usuario escribe.
 * Ej.: 1250000 -> 1.250.000. El valor guardado sigue siendo numérico.
 */
public final class MoneyFieldSupport {
    private static final NumberFormat PY_INTEGER = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
    private static final int MAX_DIGITS = 15;

    private MoneyFieldSupport() {
    }

    public static void install(TextField field) {
        if (field == null || field.getProperties().containsKey(MoneyFieldSupport.class.getName())) return;
        field.getProperties().put(MoneyFieldSupport.class.getName(), Boolean.TRUE);

        String initial = field.getText();
        field.setTextFormatter(new TextFormatter<String>(change -> {
            if (!change.isContentChange()) return change;

            String digits = digitsOnly(change.getControlNewText());
            if (digits.length() > MAX_DIGITS) return null;
            String formatted = formatDigits(digits);

            change.setRange(0, change.getControlText().length());
            change.setText(formatted);
            change.setCaretPosition(formatted.length());
            change.setAnchor(formatted.length());
            return change;
        }));

        if (initial != null && !initial.isBlank()) field.setText(formatDigits(digitsOnly(initial)));
    }

    public static String format(double value) {
        return PY_INTEGER.format(Math.round(value));
    }

    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static String formatDigits(String digits) {
        if (digits == null || digits.isEmpty()) return "";
        String normalized = digits.replaceFirst("^0+(?!$)", "");
        StringBuilder result = new StringBuilder(normalized.length() + normalized.length() / 3);
        int firstGroup = normalized.length() % 3;
        if (firstGroup == 0) firstGroup = 3;
        result.append(normalized, 0, firstGroup);
        for (int i = firstGroup; i < normalized.length(); i += 3) {
            result.append('.').append(normalized, i, i + 3);
        }
        return result.toString();
    }
}
