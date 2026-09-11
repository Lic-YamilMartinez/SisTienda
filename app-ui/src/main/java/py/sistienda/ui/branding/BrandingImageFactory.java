package py.sistienda.ui.branding;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import py.sistienda.core.model.LogoNegocio;

import java.io.ByteArrayInputStream;
import java.util.Optional;

public final class BrandingImageFactory {
    private BrandingImageFactory() {
    }

    public static Optional<Image> image(LogoNegocio logo) {
        if (logo == null || logo.tamanioBytes() == 0) return Optional.empty();
        Image image = new Image(new ByteArrayInputStream(logo.contenido()));
        return image.isError() ? Optional.empty() : Optional.of(image);
    }

    public static StackPane logoBox(LogoNegocio logo, double size, String styleClass) {
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        if (styleClass != null && !styleClass.isBlank()) box.getStyleClass().add(styleClass);

        image(logo).ifPresent(image -> {
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(size - 6);
            view.setFitHeight(size - 6);
            box.getChildren().add(view);
        });
        return box;
    }
}
