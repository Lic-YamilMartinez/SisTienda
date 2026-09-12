package py.sistienda.ui;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.Empresa;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;
import py.sistienda.core.service.EmpresaService;
import py.sistienda.core.service.LogoNegocioService;
import py.sistienda.core.service.UsuarioService;
import py.sistienda.ui.branding.BrandingImageFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

public final class MainShell extends BorderPane {

    private final Usuario usuario;
    private final AutorizacionService autorizacionService;
    private final UsuarioService usuarioService;
    private final EmpresaService empresaService;
    private final LogoNegocioService logoNegocioService;
    private final Runnable onLogout;
    private final Supplier<Node> catalogoSupplier;
    private final Supplier<Node> cajaSupplier;
    private final Supplier<Node> fiadoSupplier;
    private final Supplier<Node> reportesSupplier;
    private final Supplier<Node> comprasSupplier;
    private final Supplier<Node> configuracionSupplier;
    private final Supplier<Node> usuariosSupplier;

    private Button catalogoButton;
    private Button cajaButton;
    private Button fiadoButton;
    private Button reportesButton;
    private Button comprasButton;
    private Button configuracionButton;
    private Button usuariosButton;
    private final StackPane businessLogo = new StackPane();
    private final Label businessName = new Label("Mi Tienda");

    public MainShell(
            Supplier<Node> catalogoSupplier,
            Supplier<Node> cajaSupplier,
            Supplier<Node> fiadoSupplier,
            Supplier<Node> reportesSupplier,
            Supplier<Node> comprasSupplier,
            Supplier<Node> configuracionSupplier,
            Supplier<Node> usuariosSupplier,
            Usuario usuario,
            AutorizacionService autorizacionService,
            UsuarioService usuarioService,
            EmpresaService empresaService,
            LogoNegocioService logoNegocioService,
            Runnable onLogout
    ) {
        this.usuario = Objects.requireNonNull(usuario);
        this.catalogoSupplier = Objects.requireNonNull(catalogoSupplier);
        this.cajaSupplier = Objects.requireNonNull(cajaSupplier);
        this.fiadoSupplier = Objects.requireNonNull(fiadoSupplier);
        this.reportesSupplier = Objects.requireNonNull(reportesSupplier);
        this.comprasSupplier = Objects.requireNonNull(comprasSupplier);
        this.configuracionSupplier = Objects.requireNonNull(configuracionSupplier);
        this.usuariosSupplier = Objects.requireNonNull(usuariosSupplier);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
        this.usuarioService = Objects.requireNonNull(usuarioService);
        this.empresaService = Objects.requireNonNull(empresaService);
        this.logoNegocioService = Objects.requireNonNull(logoNegocioService);
        this.onLogout = Objects.requireNonNull(onLogout);

        getStyleClass().add("app-shell");
        setLeft(buildSidebar());
        refreshBranding();
        showCatalogo();
    }

    private VBox buildSidebar() {
        businessLogo.setMinSize(48, 48);
        businessLogo.setPrefSize(48, 48);
        businessLogo.setMaxSize(48, 48);
        businessLogo.getStyleClass().add("business-logo-box");

        businessName.getStyleClass().add("business-name");
        businessName.setWrapText(true);
        businessName.setMaxWidth(135);
        Label powered = new Label("Gestionado con SisTienda");
        powered.getStyleClass().add("business-powered");
        VBox brandText = new VBox(2, businessName, powered);
        HBox brandRow = new HBox(10, businessLogo, brandText);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand-row");

        Label section = new Label("GESTIÓN");
        section.getStyleClass().add("sidebar-section");

        catalogoButton = navButton("▦", "Catálogo & Stock");
        catalogoButton.setOnAction(event -> showCatalogo());
        configureVisibility(catalogoButton, Permiso.CATALOGO_VER);

        cajaButton = navButton("▣", "Caja");
        cajaButton.setOnAction(event -> showCaja());
        configureVisibility(cajaButton, Permiso.CAJA_OPERAR);

        fiadoButton = navButton("◎", "Clientes & Fiado");
        fiadoButton.setOnAction(event -> showFiado());
        configureVisibility(fiadoButton, Permiso.FIADO_GESTIONAR);

        reportesButton = navButton("▤", "Reportes");
        reportesButton.setOnAction(event -> showReportes());
        configureVisibility(reportesButton, Permiso.REPORTES_VER);

        comprasButton = navButton("▥", "Compras");
        comprasButton.setOnAction(event -> showCompras());
        configureVisibility(comprasButton, Permiso.COMPRAS_GESTIONAR);

        configuracionButton = navButton("⚙", "Configuración");
        configuracionButton.setOnAction(event -> showConfiguracion());
        configureVisibility(configuracionButton, Permiso.CONFIGURACION_GESTIONAR);

        usuariosButton = navButton("♟", "Usuarios & Roles");
        usuariosButton.setOnAction(event -> showUsuarios());
        configureVisibility(usuariosButton, Permiso.USUARIOS_GESTIONAR);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label(usuario.username());
        userLabel.getStyleClass().add("sidebar-user");
        Label roleLabel = new Label(usuario.rolUsuario().descripcion());
        roleLabel.getStyleClass().add("sidebar-role");

        Button password = new Button("Cambiar contraseña");
        password.getStyleClass().add("nav-button");
        password.setMaxWidth(Double.MAX_VALUE);
        password.setOnAction(event -> cambiarMiPassword());

        Button logout = new Button("Cerrar sesión");
        logout.getStyleClass().add("nav-button");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(event -> onLogout.run());

        Label version = new Label("PILOTO · 0.9.1");
        version.getStyleClass().add("sidebar-version");

        VBox sidebar = new VBox(10,
                brandRow, section,
                catalogoButton, cajaButton, fiadoButton, reportesButton, comprasButton, configuracionButton, usuariosButton,
                spacer, userLabel, roleLabel, password, logout, version
        );
        sidebar.setPadding(new Insets(24, 18, 20, 18));
        sidebar.setPrefWidth(230);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    public void refreshBranding() {
        Empresa empresa = empresaService.obtener();
        businessName.setText(empresa.nombre());
        businessLogo.getChildren().clear();
        var logo = logoNegocioService.obtener().orElse(null);
        var image = BrandingImageFactory.image(logo);
        if (image.isPresent()) {
            ImageView view = new ImageView(image.get());
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(42);
            view.setFitHeight(42);
            businessLogo.getChildren().add(view);
        } else {
            Label mark = new Label("ST");
            mark.getStyleClass().add("business-logo-mark");
            businessLogo.getChildren().add(mark);
        }
    }

    private Button navButton(String icon, String text) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("nav-icon");
        Label textLabel = new Label(text);
        HBox content = new HBox(12, iconLabel, textLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        return button;
    }

    private void configureVisibility(Button button, Permiso permiso) {
        boolean visible = autorizacionService.puede(usuario, permiso);
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void showCatalogo() {
        autorizacionService.exigir(usuario, Permiso.CATALOGO_VER);
        setCenter(catalogoSupplier.get());
        activate(catalogoButton);
    }

    private void showCaja() {
        autorizacionService.exigir(usuario, Permiso.CAJA_OPERAR);
        setCenter(cajaSupplier.get());
        activate(cajaButton);
    }

    private void showFiado() {
        autorizacionService.exigir(usuario, Permiso.FIADO_GESTIONAR);
        setCenter(fiadoSupplier.get());
        activate(fiadoButton);
    }

    private void showReportes() {
        autorizacionService.exigir(usuario, Permiso.REPORTES_VER);
        setCenter(reportesSupplier.get());
        activate(reportesButton);
    }

    private void showCompras() {
        autorizacionService.exigir(usuario, Permiso.COMPRAS_GESTIONAR);
        setCenter(comprasSupplier.get());
        activate(comprasButton);
    }

    private void showConfiguracion() {
        autorizacionService.exigir(usuario, Permiso.CONFIGURACION_GESTIONAR);
        setCenter(configuracionSupplier.get());
        activate(configuracionButton);
    }

    private void showUsuarios() {
        autorizacionService.exigir(usuario, Permiso.USUARIOS_GESTIONAR);
        setCenter(usuariosSupplier.get());
        activate(usuariosButton);
    }

    private void cambiarMiPassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Cambiar contraseña");
        dialog.setHeaderText("Actualizar contraseña de " + usuario.username());
        ButtonType guardar = new ButtonType("Guardar contraseña", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        PasswordField actual = new PasswordField();
        actual.setPromptText("Contraseña actual");
        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Nueva contraseña · mínimo 8 caracteres");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repetir nueva contraseña");
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        for (PasswordField field : new PasswordField[]{actual, nueva, confirm}) {
            field.getStyleClass().add("form-control");
            field.setMaxWidth(Double.MAX_VALUE);
        }
        VBox content = new VBox(8,
                labeled("Contraseña actual", actual),
                labeled("Nueva contraseña", nueva),
                labeled("Confirmar", confirm),
                error
        );
        content.setPrefWidth(450);
        dialog.getDialogPane().setContent(content);
        addStyle(dialog);

        Node save = dialog.getDialogPane().lookupButton(guardar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            if (!nueva.getText().equals(confirm.getText())) {
                error.setText("Las nuevas contraseñas no coinciden.");
                event.consume();
                return;
            }
            char[] oldSecret = actual.getText().toCharArray();
            char[] newSecret = nueva.getText().toCharArray();
            try {
                usuarioService.cambiarMiPassword(usuario, oldSecret, newSecret);
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            } finally {
                Arrays.fill(oldSecret, '\0');
                Arrays.fill(newSecret, '\0');
            }
        });

        dialog.showAndWait().filter(guardar::equals).ifPresent(result -> {
            Dialog<ButtonType> ok = new Dialog<>();
            if (getScene() != null) ok.initOwner(getScene().getWindow());
            ok.setTitle("Contraseña actualizada");
            ok.setHeaderText("Tu contraseña se actualizó correctamente.");
            ok.getDialogPane().getButtonTypes().add(ButtonType.OK);
            addStyle(ok);
            ok.showAndWait();
        });
    }

    private VBox labeled(String text, PasswordField field) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return new VBox(5, label, field);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause().getMessage() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private void addStyle(Dialog<?> dialog) {
        var css = MainShell.class.getResource("/styles/app.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }

    private void activate(Button activeButton) {
        for (Button button : new Button[]{catalogoButton, cajaButton, fiadoButton, reportesButton, comprasButton,
                configuracionButton, usuariosButton}) {
            if (button != null) button.getStyleClass().remove("nav-button-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
    }
}
