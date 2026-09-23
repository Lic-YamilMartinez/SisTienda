package py.sistienda.ui.venta;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.PagoVenta;
import py.sistienda.core.util.MoneyMath;
import py.sistienda.ui.common.MoneyFieldSupport;
import py.sistienda.ui.common.ResponsiveDialogSupport;
import py.sistienda.ui.common.TooltipSupport;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PagoMixtoDialog {
    private static final double EPSILON = 0.000001d;

    private PagoMixtoDialog() {
    }

    public static Optional<Distribucion> show(Window owner, double total, boolean permitirFiado) {
        Dialog<Distribucion> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Pago mixto / parcial");
        dialog.setHeaderText("Distribuí " + formatCurrency(total) + " entre las formas de pago");

        ButtonType confirmar = new ButtonType("Confirmar distribución", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);

        TextField efectivo = moneyField("0");
        TextField transferencia = moneyField("0");
        TextField tarjeta = moneyField("0");

        Label pagado = valueLabel();
        Label saldo = valueLabel();
        Label saldoTitle = new Label(permitirFiado ? "SALDO A FIAR" : "FALTA DISTRIBUIR");
        saldoTitle.getStyleClass().add("form-label");
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        Label hint = new Label(permitirFiado
                ? "Si la suma de efectivo, transferencia y tarjeta no cubre el total, la diferencia quedará como deuda del cliente."
                : "Distribuí el total completo entre efectivo, transferencia y tarjeta.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-subtitle");

        VBox totalBox = metric("TOTAL", formatCurrency(total));
        VBox pagadoBox = new VBox(3, label("PAGADO AHORA"), pagado);
        VBox saldoBox = new VBox(3, saldoTitle, saldo);
        HBox resumen = new HBox(12, totalBox, pagadoBox, saldoBox);
        resumen.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox content = new VBox(12,
                hint,
                resumen,
                field("Efectivo (Gs.)", efectivo),
                field("Transferencia (Gs.)", transferencia),
                field("Tarjeta (Gs.)", tarjeta),
                error
        );
        content.setPadding(new Insets(8));

        Runnable recalc = () -> {
            double cash = parseSilently(efectivo.getText());
            double transfer = parseSilently(transferencia.getText());
            double card = parseSilently(tarjeta.getText());
            double paid = MoneyMath.guaranies(cash + transfer + card);
            double pending = MoneyMath.guaranies(Math.max(0d, total - paid));
            pagado.setText(formatCurrency(paid));
            saldo.setText(formatCurrency(pending));
            if (paid - total > EPSILON) {
                saldo.setText("EXCEDE " + formatCurrency(paid - total));
            }
        };
        efectivo.textProperty().addListener((obs, oldValue, newValue) -> recalc.run());
        transferencia.textProperty().addListener((obs, oldValue, newValue) -> recalc.run());
        tarjeta.textProperty().addListener((obs, oldValue, newValue) -> recalc.run());
        recalc.run();

        TooltipSupport.install(efectivo, "Importe de esta venta que entra físicamente a la caja.");
        TooltipSupport.install(transferencia, "Importe de esta venta cobrado por transferencia.");
        TooltipSupport.install(tarjeta, "Importe de esta venta cobrado con tarjeta.");
        TooltipSupport.install(saldoBox, permitirFiado
                ? "Esta diferencia quedará como cuenta por cobrar del cliente. No vuelve a generar ganancia cuando se cobre."
                : "El total debe quedar completamente distribuido.");

        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);
        ResponsiveDialogSupport.fit(dialog, 660, 650);

        Distribucion[] result = {null};
        Node save = dialog.getDialogPane().lookupButton(confirmar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                double cash = parse(efectivo.getText(), "efectivo");
                double transfer = parse(transferencia.getText(), "transferencia");
                double card = parse(tarjeta.getText(), "tarjeta");
                double paid = MoneyMath.guaranies(cash + transfer + card);

                if (paid - total > EPSILON) {
                    throw new ValidationException("Lo distribuido supera el total de la venta.");
                }
                double pending = MoneyMath.guaranies(total - paid);
                if (pending > EPSILON && !permitirFiado) {
                    throw new ValidationException("Tu usuario no puede dejar saldo fiado. Completá el total entre los medios de cobro.");
                }

                List<PagoVenta> pagos = new ArrayList<>();
                if (cash > EPSILON) pagos.add(new PagoVenta(MetodoPago.EFECTIVO, MoneyMath.guaranies(cash)));
                if (transfer > EPSILON) pagos.add(new PagoVenta(MetodoPago.TRANSFERENCIA, MoneyMath.guaranies(transfer)));
                if (card > EPSILON) pagos.add(new PagoVenta(MetodoPago.TARJETA, MoneyMath.guaranies(card)));
                if (pending > EPSILON) pagos.add(new PagoVenta(MetodoPago.FIADO, pending));
                if (pagos.isEmpty()) {
                    throw new ValidationException("Ingresá al menos una forma de pago.");
                }

                result[0] = new Distribucion(List.copyOf(pagos), pending);
                error.setVisible(false);
                error.setManaged(false);
            } catch (RuntimeException e) {
                error.setText(e.getMessage() == null ? "Revisá la distribución del pago." : e.getMessage());
                error.setVisible(true);
                error.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == confirmar ? result[0] : null);
        return dialog.showAndWait();
    }

    private static TextField moneyField(String value) {
        TextField field = new TextField(value);
        field.getStyleClass().add("pos-control");
        field.setMaxWidth(Double.MAX_VALUE);
        MoneyFieldSupport.install(field);
        return field;
    }

    private static VBox field(String text, TextField field) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return new VBox(5, label, field);
    }

    private static VBox metric(String title, String value) {
        return new VBox(3, label(title), new Label(value));
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private static Label valueLabel() {
        Label label = new Label("Gs. 0");
        label.getStyleClass().add("metric-value");
        return label;
    }

    private static double parse(String value, String field) {
        if (value == null || value.isBlank()) return 0d;
        String normalized = value.trim()
                .replace("Gs.", "")
                .replace("Gs", "")
                .replace("₲", "")
                .replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double parsed = Double.parseDouble(normalized);
            if (!Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
            return MoneyMath.guaranies(parsed);
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el monto de " + field + ".");
        }
    }

    private static double parseSilently(String value) {
        try {
            return parse(value, "pago");
        } catch (RuntimeException e) {
            return 0d;
        }
    }

    private static String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
    }

    private static void applyStyles(Dialog<?> dialog) {
        var app = PagoMixtoDialog.class.getResource("/styles/app.css");
        if (app != null) dialog.getDialogPane().getStylesheets().add(app.toExternalForm());
        var venta = PagoMixtoDialog.class.getResource("/styles/venta.css");
        if (venta != null) dialog.getDialogPane().getStylesheets().add(venta.toExternalForm());
    }

    public record Distribucion(List<PagoVenta> pagos, double saldoFiado) {
        public Distribucion {
            pagos = List.copyOf(pagos);
        }
    }
}
