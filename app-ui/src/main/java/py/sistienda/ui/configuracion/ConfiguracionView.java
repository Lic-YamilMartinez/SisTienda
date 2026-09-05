package py.sistienda.ui.configuracion;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Empresa;
import py.sistienda.core.service.BackupService;
import py.sistienda.core.service.EmpresaService;

public final class ConfiguracionView extends BorderPane {

    private final EmpresaService empresaService;
    private final BackupService backupService;

    private final TextField nombre = new TextField();
    private final TextField ruc = new TextField();
    private final TextField direccion = new TextField();
    private final TextField telefono = new TextField();
    private final TextArea mensaje = new TextArea();
    private final Label feedback = new Label();

    private final Label previewNombre = new Label();
    private final Label previewRuc = new Label();
    private final Label previewDireccion = new Label();
    private final Label previewTelefono = new Label();
    private final Label previewMensaje = new Label();

    public ConfiguracionView(EmpresaService empresaService, BackupService backupService) {
        this.empresaService = empresaService;
        this.backupService = backupService;

        getStyleClass().add("content-area");
        setPadding(new Insets(20, 24, 20, 24));
        setTop(buildHeader());
        setCenter(buildContent());

        configurarPreview();
        cargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("PERSONALIZACIÓN & SEGURIDAD");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Configuración");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Personalizá tu tienda y protegé la información del negocio.");
        subtitle.getStyleClass().add("page-subtitle");

        feedback.getStyleClass().add("config-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);

        return new VBox(3, eyebrow, title, subtitle, feedback);
    }

    private HBox buildContent() {
        VBox formCard = buildFormCard();
        VBox previewCard = buildPreviewCard();
        BackupPane backupPane = new BackupPane(backupService);
        backupPane.setPadding(new Insets(18));

        VBox rightColumn = new VBox(12, previewCard, backupPane);
        rightColumn.setPrefWidth(410);
        rightColumn.setMinWidth(370);
        rightColumn.setMaxWidth(450);

        HBox.setHgrow(formCard, Priority.ALWAYS);
        formCard.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(14, formCard, rightColumn);
        content.setPadding(new Insets(14, 0, 0, 0));
        return content;
    }

    private VBox buildFormCard() {
        Label title = new Label("Datos de la tienda");
        title.getStyleClass().add("config-section-title");
        Label hint = new Label("Estos datos identifican el comercio y se imprimen en el comprobante.");
        hint.getStyleClass().add("config-hint");

        nombre.setPromptText("Ej.: Mi Tienda");
        ruc.setPromptText("RUC opcional");
        direccion.setPromptText("Dirección opcional");
        telefono.setPromptText("Teléfono opcional");
        mensaje.setPromptText("Ej.: ¡Gracias por su compra!");
        mensaje.setPrefRowCount(3);
        mensaje.setWrapText(true);

        for (var field : new javafx.scene.control.Control[]{nombre, ruc, direccion, telefono, mensaje}) {
            field.getStyleClass().add("config-input");
            field.setMaxWidth(Double.MAX_VALUE);
        }

        HBox secondary = new HBox(10, formField("RUC", ruc), formField("Teléfono", telefono));
        secondary.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        Button guardar = new Button("Guardar configuración");
        guardar.getStyleClass().add("primary-button");
        guardar.setOnAction(event -> guardar());

        VBox card = new VBox(12,
                title, hint,
                formField("Nombre de la tienda *", nombre),
                secondary,
                formField("Dirección", direccion),
                formField("Mensaje al pie del ticket", mensaje),
                guardar
        );
        card.getStyleClass().add("config-card");
        card.setPadding(new Insets(20));
        return card;
    }

    private VBox buildPreviewCard() {
        Label title = new Label("Vista previa del encabezado");
        title.getStyleClass().add("config-section-title");
        Label hint = new Label("Así se verán los datos principales en el comprobante.");
        hint.getStyleClass().add("config-hint");

        previewNombre.getStyleClass().add("config-preview-name");
        previewRuc.getStyleClass().add("config-preview-line");
        previewDireccion.getStyleClass().add("config-preview-line");
        previewTelefono.getStyleClass().add("config-preview-line");
        previewMensaje.getStyleClass().add("config-preview-message");
        previewMensaje.setWrapText(true);

        Label divider = new Label("--------------------------------");
        divider.getStyleClass().add("config-preview-divider");

        VBox paper = new VBox(6,
                previewNombre,
                previewRuc,
                previewDireccion,
                previewTelefono,
                divider,
                new Label("Ticket #000123"),
                new Label("05/09/2026 19:30"),
                new Label("..."),
                divider,
                previewMensaje
        );
        paper.setAlignment(Pos.TOP_CENTER);
        paper.getStyleClass().add("config-ticket-preview");
        paper.setPadding(new Insets(18));

        VBox card = new VBox(10, title, hint, paper);
        card.getStyleClass().add("config-card");
        card.setPadding(new Insets(18));
        return card;
    }

    private VBox formField(String labelText, javafx.scene.control.Control field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        VBox box = new VBox(5, label, field);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void configurarPreview() {
        nombre.textProperty().addListener((obs, oldValue, newValue) -> actualizarPreview());
        ruc.textProperty().addListener((obs, oldValue, newValue) -> actualizarPreview());
        direccion.textProperty().addListener((obs, oldValue, newValue) -> actualizarPreview());
        telefono.textProperty().addListener((obs, oldValue, newValue) -> actualizarPreview());
        mensaje.textProperty().addListener((obs, oldValue, newValue) -> actualizarPreview());
    }

    private void cargar() {
        ejecutar(() -> {
            Empresa empresa = empresaService.obtener();
            nombre.setText(empresa.nombre());
            ruc.setText(orEmpty(empresa.ruc()));
            direccion.setText(orEmpty(empresa.direccion()));
            telefono.setText(orEmpty(empresa.telefono()));
            mensaje.setText(orEmpty(empresa.mensajeTicket()));
            actualizarPreview();
        });
    }

    private void guardar() {
        ejecutar(() -> {
            Empresa saved = empresaService.guardar(
                    nombre.getText(), ruc.getText(), direccion.getText(), telefono.getText(), mensaje.getText()
            );
            nombre.setText(saved.nombre());
            ruc.setText(orEmpty(saved.ruc()));
            direccion.setText(orEmpty(saved.direccion()));
            telefono.setText(orEmpty(saved.telefono()));
            mensaje.setText(orEmpty(saved.mensajeTicket()));
            actualizarPreview();
            mostrarFeedback("Configuración guardada correctamente.");
        });
    }

    private void actualizarPreview() {
        previewNombre.setText(nombre.getText().isBlank() ? "Mi Tienda" : nombre.getText().trim());
        previewRuc.setText(ruc.getText().isBlank() ? "" : "RUC: " + ruc.getText().trim());
        previewDireccion.setText(direccion.getText().isBlank() ? "" : direccion.getText().trim());
        previewTelefono.setText(telefono.getText().isBlank() ? "" : "Tel: " + telefono.getText().trim());
        previewMensaje.setText(mensaje.getText().isBlank() ? "¡Gracias por su compra!" : mensaje.getText().trim());
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false);
            feedback.setManaged(false);
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
