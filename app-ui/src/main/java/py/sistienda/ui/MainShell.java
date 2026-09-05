package py.sistienda.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.Usuario;

import java.util.Objects;
import java.util.function.Supplier;

public final class MainShell extends BorderPane {

    private final Usuario usuario;
    private final Supplier<Node> catalogoSupplier;
    private final Supplier<Node> cajaSupplier;
    private final Supplier<Node> reportesSupplier;
    private final Supplier<Node> comprasSupplier;
    private final Supplier<Node> configuracionSupplier;

    private Button catalogoButton;
    private Button cajaButton;
    private Button reportesButton;
    private Button comprasButton;
    private Button configuracionButton;

    public MainShell(
            Supplier<Node> catalogoSupplier,
            Supplier<Node> cajaSupplier,
            Supplier<Node> reportesSupplier,
            Supplier<Node> comprasSupplier,
            Supplier<Node> configuracionSupplier,
            Usuario usuario
    ) {
        this.usuario = Objects.requireNonNull(usuario);
        this.catalogoSupplier = Objects.requireNonNull(catalogoSupplier);
        this.cajaSupplier = Objects.requireNonNull(cajaSupplier);
        this.reportesSupplier = Objects.requireNonNull(reportesSupplier);
        this.comprasSupplier = Objects.requireNonNull(comprasSupplier);
        this.configuracionSupplier = Objects.requireNonNull(configuracionSupplier);

        getStyleClass().add("app-shell");
        setLeft(buildSidebar());
        showCatalogo();
    }

    private VBox buildSidebar() {
        Label mark = new Label("ST");
        mark.getStyleClass().add("brand-mark");

        Label brand = new Label("SisTienda");
        brand.getStyleClass().add("brand-title");

        HBox brandRow = new HBox(12, mark, brand);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand-row");

        Label section = new Label("GESTIÓN");
        section.getStyleClass().add("sidebar-section");

        catalogoButton = navButton("▦", "Catálogo & Stock");
        catalogoButton.setOnAction(event -> showCatalogo());

        cajaButton = navButton("▣", "Caja");
        cajaButton.setOnAction(event -> showCaja());

        reportesButton = navButton("▤", "Reportes");
        reportesButton.setOnAction(event -> showReportes());

        comprasButton = navButton("▥", "Compras");
        comprasButton.setOnAction(event -> showCompras());

        configuracionButton = navButton("⚙", "Configuración");
        configuracionButton.setOnAction(event -> showConfiguracion());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Sesión: " + usuario.username());
        userLabel.getStyleClass().add("sidebar-user");

        Label version = new Label("MVP · Sprint 6");
        version.getStyleClass().add("sidebar-version");

        VBox sidebar = new VBox(10,
                brandRow, section, catalogoButton, cajaButton, reportesButton, comprasButton, configuracionButton,
                spacer, userLabel, version
        );
        sidebar.setPadding(new Insets(24, 18, 20, 18));
        sidebar.setPrefWidth(230);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
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

    private void showCatalogo() {
        setCenter(catalogoSupplier.get());
        activate(catalogoButton);
    }

    private void showCaja() {
        setCenter(cajaSupplier.get());
        activate(cajaButton);
    }

    private void showReportes() {
        setCenter(reportesSupplier.get());
        activate(reportesButton);
    }

    private void showCompras() {
        setCenter(comprasSupplier.get());
        activate(comprasButton);
    }

    private void showConfiguracion() {
        setCenter(configuracionSupplier.get());
        activate(configuracionButton);
    }

    private void activate(Button activeButton) {
        for (Button button : new Button[]{catalogoButton, cajaButton, reportesButton, comprasButton, configuracionButton}) {
            if (button != null) button.getStyleClass().remove("nav-button-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
    }
}
