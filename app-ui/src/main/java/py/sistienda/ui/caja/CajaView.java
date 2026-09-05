package py.sistienda.ui.caja;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
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
        setPadding(new Insets(28, 32, 28, 32));
        setTop(buildHeader());

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("cash-scroll");
        setCenter(scroll);

        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("OPERACIÓN");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Caja & Ventas");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Abrí la caja, registrá ventas y cerrá el turno desde un solo lugar.");
        subtitle.getStyleClass().add("page-subtitle");

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        VBox header = new VBox(8, eyebrow, title, subtitle, feedback);
        header.setPadding(new Insets(0, 0, 20, 0));
        return header;
    }

    private void recargar() {
        body.getChildren().clear();
        body.setSpacing(18);
        body.setPadding(new Insets(0, 2, 12, 0));
        cajaService.obtenerAbierta(usuario)
                .ifPresentOrElse(this::mostrarCajaAbierta, this::mostrarApertura);
    }

    private void mostrarApertura() {
        Label status = badge("Caja cerrada", "cash-status-closed");
        Label title = new Label("Abrí la caja para comenzar a vender");
        title.getStyleClass().add("cash-title");
        Label subtitle = new Label("Indicá cuánto efectivo tenés al iniciar el turno. Este valor queda registrado para el cierre.");
        subtitle.getStyleClass().add("cash-subtitle");
        subtitle.setWrapText(true);

        TextField apertura = new TextField();
        apertura.setPromptText("Ej.: 250000");
        apertura.getStyleClass().add("cash-input");

        TextArea notas = new TextArea();
        notas.setPromptText("Nota opcional, por ejemplo: fondo inicial del día");
        notas.getStyleClass().add("cash-notes");
        notas.setPrefRowCount(3);
        notas.setWrapText(true);

        Button abrir = new Button("Abrir caja");
        abrir.getStyleClass().add("primary-button");
        abrir.setOnAction(event -> ejecutar(() -> {
            cajaService.abrir(usuario, parseMonto(apertura.getText(), "monto de apertura"), notas.getText());
            mostrarFeedback("Caja abierta correctamente. Ya podés comenzar a vender.");
            recargar();
        }));

        VBox form = new VBox(10,
                fieldLabel("Monto de apertura (Gs.)"), apertura,
                fieldLabel("Notas"), notas,
                abrir
        );
        form.setMaxWidth(480);

        VBox card = new VBox(14, status, title, subtitle, form);
        card.getStyleClass().add("cash-main-card");
        card.setPadding(new Insets(26));
        body.getChildren().add(card);
    }

    private void mostrarCajaAbierta(CajaSesion sesion) {
        body.getChildren().add(buildStatusCard(sesion));

        VentaView ventaView = new VentaView(productoService, ventaService, usuario, sesion);
        ventaView.setMinHeight(520);
        body.getChildren().add(ventaView);

        body.getChildren().add(buildCloseCard(sesion));
    }

    private VBox buildStatusCard(CajaSesion sesion) {
        Label status = badge("Caja abierta", "cash-status-open");
        Label title = new Label("Caja lista para operar");
        title.getStyleClass().add("cash-title");
        Label subtitle = new Label("Abierta desde " + DATE_FORMAT.format(sesion.fechaApertura()) + ".");
        subtitle.getStyleClass().add("cash-subtitle");

        Label aperturaValue = metricValue(formatCurrency(sesion.montoApertura()));
        Label usuarioValue = metricValue(usuario.username());
        Label estadoValue = metricValue("ABIERTA");

        HBox metrics = new HBox(14,
                metricCard("Fondo inicial", aperturaValue, "Monto registrado al abrir"),
                metricCard("Usuario", usuarioValue, "Responsable de la sesión"),
                metricCard("Estado", estadoValue, "Lista para registrar ventas")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox card = new VBox(14, status, title, subtitle, metrics);
        card.getStyleClass().add("cash-main-card");
        card.setPadding(new Insets(22));
        return card;
    }

    private VBox buildCloseCard(CajaSesion sesion) {
        Label closeTitle = new Label("Cerrar caja");
        closeTitle.getStyleClass().add("cash-section-title");
        Label closeHint = new Label("Al finalizar el turno, contá el efectivo real y registralo acá.");
        closeHint.getStyleClass().add("cash-subtitle");

        TextField cierre = new TextField();
        cierre.setPromptText("Monto contado al cierre");
        cierre.getStyleClass().add("cash-input");

        TextArea notas = new TextArea();
        notas.setPromptText("Nota de cierre opcional");
        notas.getStyleClass().add("cash-notes");
        notas.setPrefRowCount(2);
        notas.setWrapText(true);

        Button cerrar = new Button("Cerrar caja");
        cerrar.getStyleClass().addAll("secondary-button", "cash-close-button");
        cerrar.setOnAction(event -> ejecutar(() -> {
            cajaService.cerrar(sesion, parseMonto(cierre.getText(), "monto de cierre"), notas.getText());
            mostrarFeedback("Caja cerrada correctamente.");
            recargar();
        }));

        VBox form = new VBox(10,
                fieldLabel("Monto contado (Gs.)"), cierre,
                fieldLabel("Notas"), notas,
                cerrar
        );
        form.setMaxWidth(480);

        VBox card = new VBox(10, closeTitle, closeHint, form);
        card.getStyleClass().add("cash-main-card");
        card.setPadding(new Insets(22));
        return card;
    }

    private VBox metricCard(String label, Label value, String hint) {
        Label title = new Label(label);
        title.getStyleClass().add("metric-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("metric-hint");
        VBox card = new VBox(6, title, value, hintLabel);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(16, 18, 15, 18));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Label metricValue(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("metric-value");
        return label;
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
}
