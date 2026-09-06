package py.sistienda.ui.etiqueta;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public final class BarcodeImageFactory {
    private BarcodeImageFactory() {
    }

    public static WritableImage render(String value, BarcodeFormat format, int width, int height) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, format, width, height);
            WritableImage image = new WritableImage(width, height);
            var writer = image.getPixelWriter();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    writer.setColor(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return image;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el código de barras.", e);
        }
    }
}
