package py.sistienda.ui.common;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Objects;

public final class ResponsiveDialogSupport {

    private static final double SCREEN_MARGIN = 48d;
    private static final double MIN_WIDTH = 360d;
    private static final double MIN_HEIGHT = 300d;

    private ResponsiveDialogSupport() {
    }

    public static void fit(Dialog<?> dialog, double preferredWidth, double preferredHeight) {
        Objects.requireNonNull(dialog);
        Rectangle2D visual = visualBounds(dialog);
        double maxWidth = Math.max(MIN_WIDTH, visual.getWidth() - SCREEN_MARGIN);
        double maxHeight = Math.max(MIN_HEIGHT, visual.getHeight() - SCREEN_MARGIN);
        double width = Math.min(preferredWidth, maxWidth);
        double height = Math.min(preferredHeight, maxHeight);

        var pane = dialog.getDialogPane();
        pane.setMinSize(0, 0);
        pane.setPrefSize(width, height);
        pane.setMaxSize(maxWidth, maxHeight);

        dialog.setOnShown(event -> {
            Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
            if (window instanceof Stage stage) {
                stage.setMaxWidth(maxWidth);
                stage.setMaxHeight(maxHeight);
                if (stage.getWidth() > maxWidth) stage.setWidth(maxWidth);
                if (stage.getHeight() > maxHeight) stage.setHeight(maxHeight);
            }
        });
    }

    /**
     * Keeps the Dialog button bar outside the scrollable area so critical
     * actions such as Guardar/Cobrar/Cancelar stay reachable on low screens.
     */
    public static void scrollContent(Dialog<?> dialog, Node content, double preferredWidth, double preferredHeight) {
        Objects.requireNonNull(content);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.getStyleClass().add("dialog-content-scroll");
        dialog.getDialogPane().setContent(scroll);
        fit(dialog, preferredWidth, preferredHeight);
    }

    private static Rectangle2D visualBounds(Dialog<?> dialog) {
        Window owner = dialog.getOwner();
        if (owner != null) {
            var screens = Screen.getScreensForRectangle(
                    owner.getX(), owner.getY(),
                    Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight())
            );
            if (!screens.isEmpty()) return screens.getFirst().getVisualBounds();
        }
        return Screen.getPrimary().getVisualBounds();
    }
}
