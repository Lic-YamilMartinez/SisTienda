package py.sistienda.ui.configuracion;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Empresa;
import py.sistienda.core.model.LogoNegocio;
import py.sistienda.core.service.BackupService;
import py.sistienda.core.service.ConfiguracionPosService;
import py.sistienda.core.service.EmpresaService;
import py.sistienda.core.service.LogoNegocioService;
import py.sistienda.ui.branding.BrandingImageFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class ConfiguracionView extends BorderPane {

    private final EmpresaService empresaService;
    private final BackupService backupService;
    private final ConfiguracionPosService configuracionPosService;
    private final LogoNegocioService logoNegocioService;
    private final Runnable onIdentityChanged;

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
    private final StackPane logoPreview = new StackPane();
    private final StackPane appLogoPreview = new StackPane();
    private LogoNegocio logoActual;

    public ConfiguracionView(EmpresaService empresaService, BackupService backupService) {
        this(empresaService, backupService, null, null, () -> {});
    }

    public ConfiguracionView(EmpresaService empresaService, BackupService backupService,
                             ConfiguracionPosService configuracionPosService) {
        this(empresaService, backupService, configuracionPosService, null, () -> {});
    }

    public ConfiguracionView(
            EmpresaService empresaService,
            BackupService backupService,
            ConfiguracionPosService configuracionPosService,
            LogoNegocioService logoNegocioService,
            Runnable onIdentityChanged
    ) {
        this.empresaService = empresaService;
        this.backupService = backupService;
        this.configuracionPosService = configuracionPosService;
        this.logoNegocioService = logoNegocioService;
        this.onIdentityChanged = onIdentityChanged == null ? () -> {} : onIdentityChanged;

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 18, 24));
        setTop(buildHeader());
        setCenter(buildContent());

        configurarPreview();
        cargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("IDENTIDAD · HARDWARE · SEGURIDAD");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Configuración");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Hacé que SisTienda se sienta parte de tu negocio y dejá listo el punto de venta.");
        subtitle.getStyleClass().add("page-subtitle");

        feedback.getStyleClass().add("config-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);
        return new VBox(3, eyebrow, title, subtitle, feedback);
    }

    private ScrollPane buildContent() {
        VBox sections = new VBox(14);
        sections.setPadding(new Insets(14, 2, 18, 2));
        sections.getChildren().add(buildBusinessSection());

        if (configuracionPosService != null) {
            HardwarePosPane hardware = new HardwarePosPane(configuracionPosService);
            hardware.setMaxWidth(Double.MAX_VALUE);
            sections.getChildren().add(hardware);
        }

        BackupPane backup = new BackupPane(backupService);
        backup.setPadding(new Insets(18));
        backup.setMaxWidth(Double.MAX_VALUE);
        sections.getChildren().add(backup);

        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("config-scroll");
        return scroll;
    }

    private VBox buildBusinessSection() {
        Label title = new Label("Mi negocio");
        title.getStyleClass().add("config-section-title");
        Label hint = new Label("Nombre, imagen y datos que identificarán esta instalación de SisTienda.");
        hint.getStyleClass().add("config-hint");

        VBox form = buildBusinessForm();
        VBox preview = buildIdentityPreview();
        form.setMinWidth(470);
        HBox.setHgrow(form, Priority.ALWAYS);
        preview.setPrefWidth(330);
        preview.setMinWidth(300);

        HBox body = new HBox(18, form, preview);
        body.setAlignment(Pos.TOP_LEFT);

        VBox card = new VBox(14, title, hint, body);
        card.getStyleClass().add("config-card");
        card.setPadding(new Insets(20));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox buildBusinessForm() {
        nombre.setPromptText("Ej.: Despensa Doña María");
        ruc.setPromptText("RUC opcional");
        direccion.setPromptText("Dirección opcional");
        telefono.setPromptText("Teléfono opcional");
        mensaje.setPromptText("Ej.: ¡Gracias por su compra!");
        mensaje.setPrefRowCount(2);
        mensaje.setWrapText(true);

        for (var field : new javafx.scene.control.Control[]{nombre, ruc, direccion, telefono, mensaje}) {
            field.getStyleClass().add("config-input");
            field.setMaxWidth(Double.MAX_VALUE);
        }

        VBox logoEditor = buildLogoEditor();
        HBox secondary = new HBox(10, formField("RUC", ruc), formField("Teléfono", telefono));
        secondary.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        Button guardar = new Button("Guardar mi negocio");
        guardar.getStyleClass().add("primary-button");
        guardar.setOnAction(event -> guardar());

        return new VBox(12,
                logoEditor,
                formField("Nombre del negocio *", nombre),
                secondary,
                formField("Dirección", direccion),
                formField("Mensaje al pie del ticket", mensaje),
                guardar
        );
    }

    private VBox buildLogoEditor() {
        logoPreview.getStyleClass().add("config-logo-preview");
        logoPreview.setMinSize(92, 92);
        logoPreview.setPrefSize(92, 92);
        logoPreview.setMaxSize(92, 92);

        Label title = new Label("Logo o foto del negocio");
        title.getStyleClass().add("form-label");
        Label hint = new Label("PNG, JPG o JPEG · máximo 5 MB. La imagen queda guardada dentro del backup de SisTienda.");
        hint.getStyleClass().add("config-hint");
        hint.setWrapText(true);

        Button choose = new Button("Elegir imagen");
        choose.getStyleClass().add("secondary-button");
        choose.setDisable(logoNegocioService == null);
        choose.setOnAction(event -> elegirLogo());

        Button remove = new Button("Quitar imagen");
        remove.getStyleClass().add("secondary-button");
        remove.setDisable(logoNegocioService == null);
        remove.setOnAction(event -> eliminarLogo());

        HBox buttons = new HBox(8, choose, remove);
        VBox copy = new VBox(4, title, hint, buttons);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox row = new HBox(12, logoPreview, copy);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(row);
        box.getStyleClass().add("config-logo-editor");
        box.setPadding(new Insets(12));
        return box;
    }

    private VBox buildIdentityPreview() {
        Label title = new Label("Así se verá tu tienda");
        title.getStyleClass().add("config-section-title-small");

        appLogoPreview.getStyleClass().add("config-app-logo");
        appLogoPreview.setMinSize(62, 62);
        appLogoPreview.setPrefSize(62, 62);
        appLogoPreview.setMaxSize(62, 62);

        previewNombre.getStyleClass().add("config-app-business-name");
        previewNombre.setWrapText(true);
        Label powered = new Label("Gestionado con SisTienda");
        powered.getStyleClass().add("config-powered");
        VBox appText = new VBox(2, previewNombre, powered);
        HBox appHeader = new HBox(11, appLogoPreview, appText);
        appHeader.setAlignment(Pos.CENTER_LEFT);
        appHeader.getStyleClass().add("config-app-preview");
        appHeader.setPadding(new Insets(14));

        Label ticketTitle = new Label("Vista previa del ticket");
        ticketTitle.getStyleClass().add("config-section-title-small");
        previewRuc.getStyleClass().add("config-preview-line");
        previewDireccion.getStyleClass().add("config-preview-line");
        previewTelefono.getStyleClass().add("config-preview-line");
        previewMensaje.getStyleClass().add("config-preview-message");
        previewMensaje.setWrapText(true);
        Label ticketBusinessName = new Label();
        ticketBusinessName.getStyleClass().add("config-preview-name");
        ticketBusinessName.textProperty().bind(previewNombre.textProperty());

        VBox paper = new VBox(5,
                ticketBusinessName,
                previewRuc,
                previewDireccion,
                previewTelefono,
                new Label("--------------------------------"),
                new Label("Ticket #000123"),
                new Label("..."),
                previewMensaje
        );
        paper.setAlignment(Pos.TOP_CENTER);
        paper.getStyleClass().add("config-ticket-preview");
        paper.setPadding(new Insets(15));

        VBox preview = new VBox(12, title, appHeader, ticketTitle, paper);
        preview.getStyleClass().add("config-preview-panel");
        preview.setPadding(new Insets(15));
        return preview;
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
            logoActual = logoNegocioService == null ? null : logoNegocioService.obtener().orElse(null);
            actualizarLogo();
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
            onIdentityChanged.run();
            mostrarFeedback("Datos del negocio guardados. SisTienda ya está usando esta identidad.");
        });
    }

    private void elegirLogo() {
        if (logoNegocioService == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elegir logo o foto del negocio");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes PNG o JPG", "*.png", "*.jpg", "*.jpeg")
        );
        File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;

        ejecutar(() -> {
            try {
                byte[] content = Files.readAllBytes(selected.toPath());
                LogoNegocio saved = logoNegocioService.guardar(content, selected.getName());
                if (BrandingImageFactory.image(saved).isEmpty()) {
                    throw new ValidationException("JavaFX no pudo abrir la imagen seleccionada.");
                }
                logoActual = saved;
                actualizarLogo();
                onIdentityChanged.run();
                mostrarFeedback("Imagen del negocio guardada correctamente.");
            } catch (IOException e) {
                throw new ValidationException("No pudimos leer la imagen seleccionada.");
            }
        });
    }

    private void eliminarLogo() {
        if (logoNegocioService == null) return;
        ejecutar(() -> {
            logoNegocioService.eliminar();
            logoActual = null;
            actualizarLogo();
            onIdentityChanged.run();
            mostrarFeedback("Imagen quitada. SisTienda mostrará la identidad por defecto.");
        });
    }

    private void actualizarLogo() {
        renderLogo(logoPreview, logoActual, 82, true);
        renderLogo(appLogoPreview, logoActual, 54, false);
    }

    private void renderLogo(StackPane target, LogoNegocio logo, double size, boolean showHint) {
        target.getChildren().clear();
        var image = BrandingImageFactory.image(logo);
        if (image.isPresent()) {
            ImageView view = new ImageView(image.get());
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(size);
            view.setFitHeight(size);
            target.getChildren().add(view);
        } else {
            Label placeholder = new Label(showHint ? "TU\nLOGO" : "ST");
            placeholder.setAlignment(Pos.CENTER);
            placeholder.getStyleClass().add("config-logo-placeholder");
            target.getChildren().add(placeholder);
        }
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
            while (current.getCause() != null) current = current.getCause();
            mostrarFeedback(current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage());
        }
    }
}
