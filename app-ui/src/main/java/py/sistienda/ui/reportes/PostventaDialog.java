package py.sistienda.ui.reportes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaPostventa;
import py.sistienda.core.model.VentaPostventaLinea;
import py.sistienda.core.service.CajaService;
import py.sistienda.core.service.PostventaService;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PostventaDialog {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PostventaDialog() {
    }

    public static void show(
            Window owner,
            PostventaService postventaService,
            CajaService cajaService,
            Usuario usuario,
            long ventaId,
            Runnable onChanged
    ) {
        VentaPostventa venta = postventaService.obtenerVenta(usuario, ventaId);

        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Postventa · Ticket #" + venta.nroTicket());
        dialog.setHeaderText("Anulación y devolución");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(760, 650);

        Label summary = new Label(
                "Ticket #" + venta.nroTicket()
                        + " · " + DATE_TIME.format(venta.fecha())
                        + " · " + venta.metodoPago().descripcion()
        );
        summary.getStyleClass().add("post-sale-summary");

        Label amounts = new Label(
                "Venta original: " + formatCurrency(venta.totalOriginal())
                        + "   ·   Devuelto: " + formatCurrency(venta.totalDevuelto())
                        + "   ·   Neto: " + formatCurrency(venta.totalNeto())
        );
        amounts.getStyleClass().add("post-sale-amounts");

        Label state = new Label();
        state.getStyleClass().add("post-sale-state");
        if (venta.anulada()) {
            state.setText("VENTA ANULADA" + (venta.motivoAnulacion() == null ? "" : " · " + venta.motivoAnulacion()));
            state.getStyleClass().add("post-sale-state-danger");
        } else if (venta.tieneDevoluciones()) {
            state.setText(venta.totalNeto() <= 0.000001d ? "DEVOLUCIÓN COMPLETA" : "DEVOLUCIÓN PARCIAL");
            state.getStyleClass().add("post-sale-state-warning");
        } else {
            state.setText("VENTA VÁLIDA");
            state.getStyleClass().add("post-sale-state-ok");
        }

        Label productsTitle = new Label("Productos disponibles para devolución");
        productsTitle.getStyleClass().add("report-section-title");
        VBox productRows = new VBox(7);
        List<LineaControl> controls = new ArrayList<>();
        for (VentaPostventaLinea linea : venta.lineas()) {
            LineaControl control = buildLine(linea);
            controls.add(control);
            productRows.getChildren().add(control.row());
        }

        TextArea motivo = new TextArea();
        motivo.setPromptText("Motivo obligatorio. Ej.: producto defectuoso, cliente cambió de producto, venta cargada por error...");
        motivo.setPrefRowCount(3);
        motivo.setWrapText(true);
        motivo.getStyleClass().add("config-input");

        Label feedback = new Label();
        feedback.getStyleClass().add("report-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button devolver = new Button("Registrar devolución");
        devolver.getStyleClass().add("primary-button");
        devolver.setDisable(venta.anulada() || venta.totalNeto() <= 0.000001d);
        devolver.setOnAction(event -> {
            try {
                CajaSesion caja = cajaService.obtenerAbierta(usuario)
                        .orElseThrow(() -> new ValidationException(
                                "Abrí tu caja antes de registrar una devolución. Así el reintegro queda en el arqueo correcto."
                        ));
                List<DevolucionLineaSolicitud> solicitudes = new ArrayList<>();
                for (LineaControl control : controls) {
                    if (!control.selected().isSelected()) continue;
                    double cantidad = parseQuantity(control.quantity().getText(), control.linea());
                    solicitudes.add(new DevolucionLineaSolicitud(control.linea().ventaDetalleId(), cantidad));
                }
                var result = postventaService.devolver(usuario, caja, ventaId, motivo.getText(), solicitudes);
                dialog.close();
                showInfo(owner,
                        "Devolución registrada",
                        "Se devolvieron " + formatCurrency(result.totalDevuelto())
                                + " del ticket #" + result.nroTicket() + ". El stock y los reportes ya fueron actualizados."
                );
                onChanged.run();
            } catch (RuntimeException e) {
                showError(feedback, rootMessage(e));
            }
        });

        Button anular = new Button("Anular venta completa");
        anular.getStyleClass().add("danger-button");
        anular.setDisable(venta.anulada() || venta.tieneDevoluciones());
        anular.setOnAction(event -> {
            try {
                String reason = motivo.getText();
                if (reason == null || reason.isBlank()) {
                    throw new ValidationException("Indicá el motivo antes de anular la venta.");
                }
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                if (owner != null) confirm.initOwner(owner);
                confirm.setTitle("Confirmar anulación");
                confirm.setHeaderText("¿Anular por completo el ticket #" + venta.nroTicket() + "?");
                confirm.setContentText("La venta dejará de sumar en caja/reportes y todo el stock será repuesto. Esta acción quedará auditada.");
                applyStyle(confirm.getDialogPane());
                if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;

                postventaService.anular(usuario, ventaId, reason);
                dialog.close();
                showInfo(owner,
                        "Venta anulada",
                        "El ticket #" + venta.nroTicket() + " fue anulado y el stock fue repuesto."
                );
                onChanged.run();
            } catch (RuntimeException e) {
                showError(feedback, rootMessage(e));
            }
        });

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actions = new HBox(8, anular, actionSpacer, devolver);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label(
                "Anular corrige una venta cargada por error y sólo está disponible mientras la caja original siga abierta. "
                        + "Para una venta de una caja ya cerrada, utilizá devolución."
        );
        note.setWrapText(true);
        note.getStyleClass().add("post-sale-note");

        VBox content = new VBox(10,
                summary, amounts, state,
                new javafx.scene.control.Separator(),
                productsTitle, productRows,
                new javafx.scene.control.Separator(),
                labeled("Motivo *", motivo),
                feedback, note, actions
        );
        content.setPadding(new Insets(5));
        dialog.getDialogPane().setContent(content);
        applyStyle(dialog.getDialogPane());
        dialog.showAndWait();
    }

    private static LineaControl buildLine(VentaPostventaLinea linea) {
        CheckBox selected = new CheckBox();
        boolean available = linea.cantidadDisponible() > 0.000001d;
        selected.setDisable(!available);

        Label product = new Label(linea.producto());
        product.getStyleClass().add("post-sale-product");
        Label detail = new Label(
                "Vendido: " + formatQuantity(linea.cantidadVendida(), linea.unidadMedida())
                        + " · Ya devuelto: " + formatQuantity(linea.cantidadDevuelta(), linea.unidadMedida())
                        + " · Disponible: " + formatQuantity(linea.cantidadDisponible(), linea.unidadMedida())
        );
        detail.getStyleClass().add("post-sale-product-detail");
        VBox description = new VBox(2, product, detail);
        HBox.setHgrow(description, Priority.ALWAYS);

        TextField quantity = new TextField();
        quantity.setPromptText(linea.unidadMedida() == UnidadMedida.UN ? "Cant." : "Kg");
        quantity.setPrefWidth(95);
        quantity.setDisable(true);
        quantity.getStyleClass().add("config-input");
        selected.selectedProperty().addListener((obs, oldValue, newValue) -> {
            quantity.setDisable(!newValue);
            if (newValue && quantity.getText().isBlank()) {
                quantity.setText(linea.unidadMedida() == UnidadMedida.UN
                        ? Long.toString(Math.round(Math.min(1d, linea.cantidadDisponible())))
                        : formatPlain(linea.cantidadDisponible()));
            }
        });

        Label amount = new Label(formatCurrency(linea.subtotalDisponible()));
        amount.getStyleClass().add("post-sale-line-amount");
        amount.setPrefWidth(115);
        amount.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(9, selected, description, quantity, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("post-sale-line");
        row.setPadding(new Insets(8, 10, 8, 10));
        return new LineaControl(linea, selected, quantity, row);
    }

    private static VBox labeled(String text, Node node) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return new VBox(5, label, node);
    }

    private static double parseQuantity(String raw, VentaPostventaLinea linea) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Indicá la cantidad a devolver de “" + linea.producto() + "”.");
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ValidationException("La cantidad de “" + linea.producto() + "” no es válida.");
        }
    }

    private static String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private static String formatQuantity(double value, UnidadMedida unit) {
        if (unit == UnidadMedida.UN) {
            return Long.toString(Math.round(value)) + " un";
        }
        return String.format(new Locale("es", "PY"), "%.3f kg", value);
    }

    private static String formatPlain(double value) {
        String text = String.format(Locale.US, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static void showError(Label feedback, String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private static void showInfo(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        applyStyle(alert.getDialogPane());
        alert.showAndWait();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause().getMessage() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private static void applyStyle(javafx.scene.control.DialogPane pane) {
        var appCss = PostventaDialog.class.getResource("/styles/app.css");
        var reportsCss = PostventaDialog.class.getResource("/styles/reportes.css");
        if (appCss != null) pane.getStylesheets().add(appCss.toExternalForm());
        if (reportsCss != null) pane.getStylesheets().add(reportsCss.toExternalForm());
    }

    private record LineaControl(
            VentaPostventaLinea linea,
            CheckBox selected,
            TextField quantity,
            HBox row
    ) {
    }
}
