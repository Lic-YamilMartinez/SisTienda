package py.sistienda.ui.common;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.util.Objects;

public final class ResponsiveDialogSupport {

    private static final double SCREEN_MARGIN = 48d;
    private static final double MIN_WIDTH = 420d;
    private static final double MIN_HEIGHT = 340d;

    private ResponsiveDialogSupport() {
    }

    public static void fit(Dialog<?> dialog, double preferredWidth, double preferredHeight) {
        Objects.requireNonNull(dialog);
        Rectangle2D visual = visualBounds(dialog);
        double maxWidth = Math.max(MIN_WIDTH, visual.getWidth() - SCREEN_MARGIN);
        double maxHeight = Math.max(MIN_HEIGHT, visual.getHeight() - SCREEN_MARGIN);
        double width = clamp(preferredWidth, MIN_WIDTH, maxWidth);
        double height = clamp(preferredHeight, MIN_HEIGHT, maxHeight);

        var pane = dialog.getDialogPane();
        pane.setMinSize(0, 0);
        pane.setPrefSize(width, height);
        pane.setMaxSize(maxWidth, maxHeight);
        dialog.setResizable(true);

        // JavaFX/Linux can initially create a Dialog window smaller than the
        // DialogPane preferred size. Force the real window size after it exists.
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event ->
                Platform.runLater(() -> resizeAndCenter(dialog, width, height)));
    }

    public static void fitDefault(Dialog<?> dialog) {
        fit(dialog, 720, 620);
    }

    public static void fitCompact(Dialog<?> dialog) {
        fit(dialog, 600, 500);
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

    private static void resizeAndCenter(Dialog<?> dialog, double preferredWidth, double preferredHeight) {
        if (dialog.getDialogPane().getScene() == null) return;
        Window window = dialog.getDialogPane().getScene().getWindow();
        if (window == null) return;

        Rectangle2D visual = visualBounds(dialog);
        double width = clamp(preferredWidth, MIN_WIDTH, Math.max(MIN_WIDTH, visual.getWidth() - SCREEN_MARGIN));
        double height = clamp(preferredHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, visual.getHeight() - SCREEN_MARGIN));

        window.setWidth(width);
        window.setHeight(height);

        Window owner = dialog.getOwner();
        double targetX = owner == null
                ? visual.getMinX() + (visual.getWidth() - width) / 2d
                : owner.getX() + (owner.getWidth() - width) / 2d;
        double targetY = owner == null
                ? visual.getMinY() + (visual.getHeight() - height) / 2d
                : owner.getY() + (owner.getHeight() - height) / 2d;

        double maxX = visual.getMaxX() - width;
        double maxY = visual.getMaxY() - height;
        window.setX(clamp(targetX, visual.getMinX(), Math.max(visual.getMinX(), maxX)));
        window.setY(clamp(targetY, visual.getMinY(), Math.max(visual.getMinY(), maxY)));
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
