package py.sistienda.ui.common;

import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public final class TooltipSupport {
    private TooltipSupport() {
    }

    public static void install(Node node, String text) {
        if (node == null || text == null || text.isBlank()) return;
        Tooltip.install(node, create(text));
    }

    public static <T> void fullText(TableColumn<T, String> column) {
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Tooltip tooltip = create("");

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                tooltip.setText(item);
                setTooltip(tooltip);
            }
        });
    }

    private static Tooltip create(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setHideDelay(Duration.millis(100));
        tooltip.setShowDuration(Duration.seconds(20));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(420);
        return tooltip;
    }
}
