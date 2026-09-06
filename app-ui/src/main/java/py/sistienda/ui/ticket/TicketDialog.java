package py.sistienda.ui.ticket;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.model.Empresa;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaDetalleItem;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TicketDialog {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private TicketDialog() {
    }

    public static void show(Empresa empresa, VentaDetalle detalle) {
        show(empresa, detalle, ConfiguracionPos.porDefecto());
    }

    public static void show(Empresa empresa, VentaDetalle detalle, ConfiguracionPos configuracion) {
        ConfiguracionPos config = configuracion == null ? ConfiguracionPos.porDefecto() : configuracion;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ticket #" + detalle.nroTicket());
        dialog.setHeaderText("Vista previa del comprobante · " + config.anchoTicketMm() + " mm");

        ButtonType printType = new ButtonType("Imprimir", ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(printType, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(config.anchoTicketMm() == 58 ? 390 : 470);
        dialog.getDialogPane().setPrefHeight(700);

        VBox ticket = buildTicket(empresa, detalle, config);
        dialog.getDialogPane().setContent(ticket);
        addStyle(dialog, "/styles/app.css");
        addStyle(dialog, "/styles/ticket.css");

        Node printButton = dialog.getDialogPane().lookupButton(printType);
        printButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            print(ticket);
        });

        if (config.imprimirTicketAutomatico()) {
            dialog.setOnShown(event -> Platform.runLater(() -> print(ticket)));
        }
        dialog.showAndWait();
    }

    private static VBox buildTicket(Empresa empresa, VentaDetalle detalle, ConfiguracionPos config) {
        Label company = new Label(empresa.nombre());
        company.getStyleClass().add("ticket-company");
        company.setWrapText(true);

        VBox header = new VBox(3, company);
        header.setAlignment(Pos.TOP_CENTER);
        addOptional(header, empresa.ruc() == null ? null : "RUC: " + empresa.ruc(), "ticket-center-line");
        addOptional(header, empresa.direccion(), "ticket-center-line");
        addOptional(header, empresa.telefono() == null ? null : "Tel: " + empresa.telefono(), "ticket-center-line");

        Label ticketNumber = new Label("TICKET #" + detalle.nroTicket());
        ticketNumber.getStyleClass().add("ticket-number");
        Label meta = new Label(DATE_TIME.format(detalle.fecha()) + "  ·  " + detalle.usuario());
        meta.getStyleClass().add("ticket-center-line");

        VBox items = new VBox(8);
        for (VentaDetalleItem item : detalle.items()) items.getChildren().add(buildItem(item));

        Label payment = new Label("Pago: " + detalle.metodoPago().descripcion());
        payment.getStyleClass().add("ticket-info");
        VBox paymentInfo = new VBox(3, payment);
        if (detalle.metodoPago() == MetodoPago.EFECTIVO) {
            paymentInfo.getChildren().addAll(
                    infoRow("Recibido", formatCurrency(detalle.recibido())),
                    infoRow("Vuelto", formatCurrency(detalle.vuelto()))
            );
        }

        HBox total = infoRow("TOTAL", formatCurrency(detalle.total()));
        total.getStyleClass().add("ticket-total-row");

        VBox footer = new VBox(5);
        footer.setAlignment(Pos.TOP_CENTER);
        String message = empresa.mensajeTicket();
        if (message == null || message.isBlank()) message = "¡Gracias por su compra!";
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("ticket-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(config.anchoTicketMm() == 58 ? 240 : 330);
        footer.getChildren().add(messageLabel);

        VBox ticket = new VBox(10,
                header, separator(), ticketNumber, meta, separator(), items, separator(), paymentInfo,
                total, separator(), footer
        );
        ticket.getStyleClass().add("ticket-paper");
        ticket.setPadding(new Insets(config.anchoTicketMm() == 58 ? 14 : 22));
        double width = config.anchoTicketMm() == 58 ? 280 : 360;
        ticket.setPrefWidth(width);
        ticket.setMaxWidth(width);
        return ticket;
    }

    private static VBox buildItem(VentaDetalleItem item) {
        Label name = new Label(item.producto());
        name.getStyleClass().add("ticket-item-name");
        name.setWrapText(true);
        Label qtyPrice = new Label(formatQuantity(item.cantidad()) + " x " + formatCurrency(item.precioUnitario()));
        qtyPrice.getStyleClass().add("ticket-item-detail");
        Label subtotal = new Label(formatCurrency(item.subtotal()));
        subtotal.getStyleClass().add("ticket-item-subtotal");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox line = new HBox(8, qtyPrice, spacer, subtotal);
        line.setAlignment(Pos.CENTER_LEFT);
        return new VBox(2, name, line);
    }

    private static HBox infoRow(String labelText, String valueText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("ticket-info");
        Label value = new Label(valueText);
        value.getStyleClass().add("ticket-info-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, label, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label separator() {
        Label label = new Label("----------------------------------------");
        label.getStyleClass().add("ticket-separator");
        return label;
    }

    private static void addOptional(VBox parent, String text, String styleClass) {
        if (text == null || text.isBlank()) return;
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        parent.getChildren().add(label);
    }

    private static void print(VBox ticket) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showError("No se encontró una impresora disponible.");
            return;
        }
        if (!job.showPrintDialog(ticket.getScene() == null ? null : ticket.getScene().getWindow())) return;
        ticket.applyCss();
        ticket.layout();
        if (!job.printPage(ticket)) {
            job.cancelJob();
            showError("No se pudo imprimir el ticket.");
            return;
        }
        job.endJob();
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Impresión");
        addStyle(alert, "/styles/app.css");
        addStyle(alert, "/styles/ticket.css");
        alert.showAndWait();
    }

    private static String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private static String formatQuantity(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void addStyle(Dialog<?> dialog, String path) {
        var css = TicketDialog.class.getResource(path);
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
