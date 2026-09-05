package py.sistienda.ui.caja;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.CajaService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.VentaService;
import py.sistienda.ui.venta.VentaView;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class CajaView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CajaService cajaService;
    private final ProductoService productoService;
    private final VentaService ventaService;
    private final Usuario usuario;

    private final VBox body = new VBox();
    private final Label feedback = new Label();
    private final Label ventasEfectivo = new Label("Gs. 0");
    private final Label ventasTransferencia = new Label("Gs. 0");
    private final Label ventasTarjeta = new Label("Gs. 0");
    private final Label ventasTotal = new Label("Gs. 0");

    public CajaView(
            CajaService cajaService,
            ProductoService productoService,
            VentaService ventaService,
            Usuario usuario
    ) {
        this.cajaService = cajaService;
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.usuario = usuario;

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 20, 24));
        setTop(buildHeader());
        setCenter(body);
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("OPERACIÓN");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Caja & Ventas");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Vendé rápido con productos, carrito y cobro siempre visibles.");
        subtitle.getStyleClass().add("page-subtitle");

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        HBox titleRow = new HBox(12, title, subtitle);
        titleRow.setAlignment(Pos.BASELINE_LEFT);

        VBox header = new VBox(3, eyebrow, titleRow, feedback);
        header.setPadding(new Insets(0, 0, 10, 0));
        return header;
    }

    private void recargar() {
        body.getChildren().clear();
        body.setSpacing(9);
        body.setPadding(Insets.EMPTY);
        body.setAlignment(Pos.TOP_LEFT);

        cajaService.obtenerAbierta(usuario)
                .ifPresentOrElse(this::mostrarCajaAbierta, this::mostrarApertura);
    }

    private void mostrarApertura() {
        body.setAlignment(Pos.TOP_CENTER);

        Label status = badge("Caja cerrada", "cash-status-closed");
        Label title = new Label("Abrí la caja para empezar a vender");
        title.getStyleClass().add("cash-title");
        Label subtitle = new Label("Registrá el fondo inicial. La apertura queda guardada para el cierre del turno.");
        subtitle.getStyleClass().add("cash-subtitle");
        subtitle.setWrapText(true);

        TextField apertura = new TextField();
        apertura.setPromptText("Ej.: 250000");
        apertura.getStyleClass().add("cash-input");

        TextField notas = new TextField();
        notas.setPromptText("Nota opcional");
        notas.getStyleClass().add("cash-input");

        Button abrir = new Button("Abrir caja y vender");
        abrir.getStyleClass().add("primary-button");
        abrir.setOnAction(event -> ejecutar(() -> {
            cajaService.abrir(usuario, parseMonto(apertura.getText(), "monto de apertura"), notas.getText());
            mostrarFeedback("Caja abierta correctamente.");
            recargar();
        }));

        HBox fields = new HBox(10,
                compactField("Fondo inicial (Gs.)", apertura),
                compactField("Nota", notas)
        );
        fields.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox card = new VBox(12, status, title, subtitle, fields, abrir);
        card.getStyleClass().addAll("cash-main-card", "cash-open-card");
        card.setPadding(new Insets(22));
        card.setMaxWidth(760);
        body.getChildren().add(card);
    }

    private void mostrarCajaAbierta(CajaSesion sesion) {
        HBox statusBar = buildStatusBar(sesion);
        HBox resumen = buildSalesSummaryBar(sesion);
        VentaView ventaView = new VentaView(
                productoService,
                ventaService,
                usuario,
                sesion,
                () -> actualizarResumenVentas(sesion)
        );

        ventaView.setMinHeight(0);
        ventaView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(ventaView, Priority.ALWAYS);

        body.getChildren().addAll(statusBar, resumen, ventaView);
        VBox.setVgrow(ventaView, Priority.ALWAYS);
    }

    private HBox buildStatusBar(CajaSesion sesion) {
        Label status = badge("Caja abierta", "cash-status-open");

        Label opened = new Label("Desde " + DATE_FORMAT.format(sesion.fechaApertura()));
        opened.getStyleClass().add("cash-bar-detail");

        Label fund = new Label("Fondo: " + formatCurrency(sesion.montoApertura()));
        fund.getStyleClass().add("cash-bar-detail");

        Label user = new Label("Usuario: " + usuario.username());
        user.getStyleClass().add("cash-bar-detail");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button close = new Button("Cerrar caja");
        close.getStyleClass().addAll("secondary-button", "cash-close-button");
        close.setOnAction(event -> mostrarDialogoCierre(sesion));

        HBox bar = new HBox(12, status, opened, separator(), fund, separator(), user, spacer, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("cash-status-bar");
        bar.setPadding(new Insets(8, 10, 8, 12));
        return bar;
    }

    private HBox buildSalesSummaryBar(CajaSesion sesion) {
        HBox bar = new HBox(8,
                salesMetric("EFECTIVO", ventasEfectivo, false),
                salesMetric("TRANSFERENCIA", ventasTransferencia, false),
                salesMetric("TARJETA", ventasTarjeta, false),
                salesMetric("TOTAL VENDIDO", ventasTotal, true)
        );
        bar.getStyleClass().add("cash-sales-summary");
        bar.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        actualizarResumenVentas(sesion);
        return bar;
    }

    private VBox salesMetric(String titleText, Label value, boolean totalMetric) {
        Label title = new Label(titleText);
        title.getStyleClass().add("cash-sales-label");
        value.getStyleClass().removeAll("cash-sales-value", "cash-sales-value-total");
        value.getStyleClass().add("cash-sales-value");
        if (totalMetric) {
            value.getStyleClass().add("cash-sales-value-total");
        }

        VBox card = new VBox(1, title, value);
        card.getStyleClass().add("cash-sales-metric");
        if (totalMetric) {
            card.getStyleClass().add("cash-sales-metric-total");
        }
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void actualizarResumenVentas(CajaSesion sesion) {
        ResumenVentasCaja resumen = cajaService.resumenVentas(sesion);
        ventasEfectivo.setText(formatCurrency(resumen.efectivo()));
        ventasTransferencia.setText(formatCurrency(resumen.transferencia()));
        ventasTarjeta.setText(formatCurrency(resumen.tarjeta()));
        ventasTotal.setText(formatCurrency(resumen.total()));
    }

    private Region separator() {
        Region separator = new Region();
        separator.getStyleClass().add("cash-bar-separator");
        return separator;
    }

    private void mostrarDialogoCierre(CajaSesion sesion) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cerrar caja");
        dialog.setHeaderText("Finalizar turno");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        TextField cierre = new TextField();
        cierre.setPromptText("Monto contado");
        cierre.getStyleClass().add("cash-input");

        TextField notas = new TextField();
        notas.setPromptText("Nota opcional");
        notas.getStyleClass().add("cash-input");

        Label hint = new Label("Contá el efectivo real y registralo antes de cerrar.");
        hint.getStyleClass().add("cash-subtitle");
        hint.setWrapText(true);

        VBox content = new VBox(9,
                hint,
                fieldLabel("Monto contado (Gs.)"), cierre,
                fieldLabel("Nota"), notas
        );
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(430);
        applyDialogStyles(dialog);

        dialog.showAndWait()
                .filter(ButtonType.OK::equals)
                .ifPresent(result -> ejecutar(() -> {
                    cajaService.cerrar(sesion, parseMonto(cierre.getText(), "monto de cierre"), notas.getText());
                    mostrarFeedback("Caja cerrada correctamente.");
                    recargar();
                }));
    }

    private VBox compactField(String labelText, TextField field) {
        Label label = fieldLabel(labelText);
        VBox box = new VBox(5, label, field);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("cash-status", styleClass);
        return label;
    }

    private double parseMonto(String value, String campo) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Ingresá el " + campo + ".");
        }
        String normalized = value.trim()
                .replace("Gs.", "")
                .replace("Gs", "")
                .replace("₲", "")
                .replace(" ", "");
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) {
            normalized = normalized.replace(".", "");
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el " + campo + ". Usá sólo números.");
        }
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void ejecutar(Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            mostrarFeedback(e.getMessage());
        } catch (RuntimeException e) {
            Throwable current = e;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            mostrarFeedback(current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage());
        }
    }

    private void applyDialogStyles(Dialog<?> dialog) {
        addDialogStyle(dialog, "/styles/app.css");
        addDialogStyle(dialog, "/styles/caja.css");
    }

    private void addDialogStyle(Dialog<?> dialog, String path) {
        var css = CajaView.class.getResource(path);
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
    }
}
