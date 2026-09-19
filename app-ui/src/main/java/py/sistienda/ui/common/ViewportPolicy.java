package py.sistienda.ui.common;

public final class ViewportPolicy {

    private ViewportPolicy() {
    }

    public static WindowSize window(
            double visualWidth,
            double visualHeight,
            double preferredWidth,
            double preferredHeight,
            double minimumWidth,
            double minimumHeight,
            double margin
    ) {
        double maxWidth = Math.max(320d, visualWidth - Math.max(0d, margin));
        double maxHeight = Math.max(260d, visualHeight - Math.max(0d, margin));
        return new WindowSize(
                Math.min(preferredWidth, maxWidth),
                Math.min(preferredHeight, maxHeight),
                Math.min(minimumWidth, maxWidth),
                Math.min(minimumHeight, maxHeight)
        );
    }

    public record WindowSize(
            double width,
            double height,
            double minimumWidth,
            double minimumHeight
    ) {
    }
}
