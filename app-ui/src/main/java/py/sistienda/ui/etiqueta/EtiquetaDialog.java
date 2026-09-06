package py.sistienda.ui.etiqueta;

import com.google.zxing.BarcodeFormat;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.service.CodigoBarrasService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class EtiquetaDialog {
    private EtiquetaDialog() {
    }

    public static void show(Window owner, Producto producto, ConfiguracionPos configuracion,
                            CodigoBarrasService codigoBarrasService) {
        if (producto.unidadMedida() == UnidadMedida.KG) {
            pedirPeso(owner, producto, configuracion, codigoBarrasService);
        } else {
            mostrar(owner, producto, configuracion, codigoBarrasService, null);
        }
    }

    private static void pedirPeso(Window owner, Producto producto, ConfiguracionPos configuracion,
                                  CodigoBarrasService codigoBarrasService) {
        if (producto.pluBalanza() == null) {
            showError(owner, "Este producto todavía no tiene PLU de balanza.");
            return;
        }
        TextInputDialog input = new TextInputDialog("0,500");
        input.initOwner(owner);
        input.setTitle("Etiqueta por peso");
        input.setHeaderText(producto.nombre() + " · PLU " + String.format("%05d", producto.pluBalanza()));
        input.setContentText("Peso en kg:");
        applyStyle(input);
        input.showAndWait().ifPresent(value -> {
            try {
                double peso = parsePeso(value);
                mostrar(owner, producto, configuracion, codigoBarrasService, peso);
            } catch (RuntimeException e) {
                showError(owner, rootMessage(e));
            }
        });
    }

    private static void mostrar(Window owner, Producto producto, ConfiguracionPos configuracion,
                                CodigoBarrasService codigoBarrasService, Double pesoKg) {
        String codigo;
        BarcodeFormat format;
        if (pesoKg == null) {
            if (producto.codigoBarras() == null || producto.codigoBarras().isBlank()) {
                throw new ValidationException("El producto no tiene código de barras.");
            }
            codigo = producto.codigoBarras();
            format = codigoBarrasService.esEan13Valido(codigo) ? BarcodeFormat.EAN_13 : BarcodeFormat.CODE_128;
        } else {
            codigo = codigoBarrasService.generarCodigoPeso(configuracion.prefijoPeso(), producto.pluBalanza(), pesoKg);
            format = BarcodeFormat.EAN_13;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Etiqueta · " + producto.nombre());
        dialog.setHeaderText("Vista previa de etiqueta");
        ButtonType imprimir = new ButtonType("Imprimir", ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(imprimir, ButtonType.CLOSE);
        VBox label = buildLabel(producto, codigo, format, pesoKg, configuracion);
        dialog.getDialogPane().setContent(label);
        dialog.getDialogPane().setPrefWidth(430);
        applyStyle(dialog);

        Node printButton = dialog.getDialogPane().lookupButton(imprimir);
        printButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            print(label);
        });
        dialog.showAndWait();
    }

    private static VBox buildLabel(Producto producto, String codigo, BarcodeFormat format,
                                   Double pesoKg, ConfiguracionPos configuracion) {
        Label name = new Label(producto.nombre());
        name.setStyle("-fx-font-size: 16px; -fx-font-weight: 900;");
        name.setWrapText(true);
        name.setMaxWidth(320);

        VBox details = new VBox(2);
        details.setAlignment(Pos.CENTER);
        if (pesoKg == null) {
            details.getChildren().add(new Label(formatCurrency(producto.precioVenta())));
        } else {
            details.getChildren().addAll(
                    new Label("Peso: " + formatQty(pesoKg) + " kg"),
                    new Label("Precio/kg: " + formatCurrency(producto.precioVenta())),
                    new Label("Total: " + formatCurrency(producto.precioVenta() * pesoKg))
            );
        }

        ImageView barcode = new ImageView(BarcodeImageFactory.render(codigo, format, 300, 95));
        barcode.setPreserveRatio(false);
        barcode.setFitWidth(300);
        barcode.setFitHeight(95);

        Label code = new Label(codigo);
        code.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        Label hint = new Label(pesoKg == null
                ? "Código interno / fabricante"
                : "Formato balanza: " + configuracion.prefijoPeso() + " + PLU + gramos + verificador");
        hint.setStyle("-fx-font-size: 9px; -fx-text-fill: #667085;");

        VBox paper = new VBox(7, name, details, barcode, code, hint);
        paper.setAlignment(Pos.TOP_CENTER);
        paper.setPadding(new Insets(16));
        paper.setStyle("-fx-background-color: white; -fx-border-color: #d0d5dd; -fx-border-radius: 8; -fx-background-radius: 8;");
        double width = configuracion.anchoEtiquetaMm() <= 58 ? 340 : 390;
        paper.setPrefWidth(width);
        paper.setMaxWidth(width);
        return paper;
    }

    private static void print(VBox label) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showError(label.getScene() == null ? null : label.getScene().getWindow(), "No se encontró una impresora disponible.");
            return;
        }
        Window owner = label.getScene() == null ? null : label.getScene().getWindow();
        if (!job.showPrintDialog(owner)) return;
        label.applyCss();
        label.layout();
        if (!job.printPage(label)) {
            job.cancelJob();
            showError(owner, "No se pudo imprimir la etiqueta.");
            return;
        }
        job.endJob();
    }

    private static double parsePeso(String value) {
        if (value == null || value.isBlank()) throw new ValidationException("Ingresá el peso.");
        String normalized = value.trim().replace(" ", "");
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        }
        try {
            double result = Double.parseDouble(normalized);
            if (!Double.isFinite(result) || result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el peso. Ejemplo: 0,735");
        }
    }

    private static String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private static String formatQty(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "No se pudo completar la operación." : current.getMessage();
    }

    private static void showError(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        alert.setHeaderText("Etiquetas");
        applyStyle(alert);
        alert.showAndWait();
    }

    private static void applyStyle(Dialog<?> dialog) {
        var css = EtiquetaDialog.class.getResource("/styles/app.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
