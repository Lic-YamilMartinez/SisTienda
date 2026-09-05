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

public final class MainShell extends BorderPane {

    private final Usuario usuario;

    public MainShell(Node content, Usuario usuario) {
        this.usuario = usuario;
        getStyleClass().add("app-shell");
        setLeft(buildSidebar());
        setCenter(content);
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

        Button catalogo = navButton("▦", "Catálogo & Stock", true);
        Button caja = navButton("▣", "Caja", false);
        Button reportes = navButton("▤", "Reportes", false);
        Button configuracion = navButton("⚙", "Configuración", false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label("Sesión: " + usuario.username());
        userLabel.getStyleClass().add("sidebar-user");

        Label version = new Label("MVP · Sprint 2");
        version.getStyleClass().add("sidebar-version");

        VBox sidebar = new VBox(10,
                brandRow, section, catalogo, caja, reportes, configuracion,
                spacer, userLabel, version
        );
        sidebar.setPadding(new Insets(24, 18, 20, 18));
        sidebar.setPrefWidth(230);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private Button navButton(String icon, String text, boolean active) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("nav-icon");
        Label textLabel = new Label(text);
        HBox content = new HBox(12, iconLabel, textLabel);
        content.setAlignment(Pos.CENTER_LEFT);

        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");
        if (active) {
            button.getStyleClass().add("nav-button-active");
        } else {
            button.setDisable(true);
        }
        return button;
    }
}
