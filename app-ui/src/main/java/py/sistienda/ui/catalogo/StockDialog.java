package py.sistienda.ui.catalogo;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.model.UnidadMedida;

import java.math.BigDecimal;

public final class StockDialog extends Dialog<StockDialog.StockForm> {

    private static final ButtonType REGISTRAR = new ButtonType("Registrar movimiento", ButtonBar.ButtonData.OK_DONE);

    private final Producto producto;
    private final RadioButton entrada = new RadioButton("Entrada");
    private final RadioButton salida = new RadioButton("Salida");
    private final TextField cantidad = new TextField();
    private final ComboBox<String> motivo = new ComboBox<>();
    private final TextField referencia = new TextField();
    private final TextArea observacion = new TextArea();
    private final Label error = new Label();

    public StockDialog(Window owner, Producto producto) {
        this.producto = producto;
        initOwner(owner);
        setTitle("Movimiento de stock");
        setHeaderText(null);

        ToggleGroup group = new ToggleGroup();
        entrada.setToggleGroup(group);
        salida.setToggleGroup(group);
        entrada.setSelected(true);
        entrada.getStyleClass().add("stock-toggle");
        salida.getStyleClass().add("stock-toggle");

        cantidad.setPromptText(producto.unidadMedida() == UnidadMedida.UN ? "Ej.: 10" : "Ej.: 2,5");
        motivo.getItems().setAll("Compra", "Ajuste de inventario", "Devolución", "Merma", "Uso interno", "Otro");
        motivo.setEditable(true);
        motivo.setPromptText("Seleccionar o escribir motivo");
        referencia.setPromptText("Factura, proveedor u otra referencia (opcional)");
        observacion.setPromptText("Detalle adicional (opcional)");
        observacion.setPrefRowCount(3);
        observacion.setWrapText(true);

        cantidad.getStyleClass().add("form-control");
        motivo.getStyleClass().add("form-control");
        referencia.getStyleClass().add("form-control");
        observacion.getStyleClass().add("form-control");
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().addAll(REGISTRAR, ButtonType.CANCEL);
        pane.setContent(buildContent());
        pane.getStyleClass().add("product-dialog");
        applyStyles(pane);

        Node registerButton = pane.lookupButton(REGISTRAR);
        registerButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validar();
                error.setText("");
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        setResultConverter(button -> {
            if (button != REGISTRAR) {
                return null;
            }
            return new StockForm(
                    salida.isSelected() ? TipoMovimientoStock.SALIDA : TipoMovimientoStock.ENTRADA,
                    parseCantidad(),
                    motivoActual(),
                    referencia.getText(),
                    observacion.getText()
            );
        });
    }

    private VBox buildContent() {
        Label title = new Label(producto.nombre());
        title.getStyleClass().add("dialog-title");

        Label stock = new Label("Stock actual: " + formatStock(producto));
        stock.getStyleClass().add("stock-current");

        Label hint = new Label("Cada entrada o salida queda registrada en el historial y actualiza el stock automáticamente.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-subtitle");

        HBox typeRow = new HBox(10, entrada, salida);
        typeRow.getStyleClass().add("stock-toggle-row");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(9);
        grid.setPadding(new Insets(10, 0, 0, 0));

        addField(grid, 0, "Tipo de movimiento", typeRow);
        addField(grid, 1, "Cantidad", cantidad);
        addField(grid, 2, "Motivo", motivo);
        addField(grid, 3, "Referencia", referencia);
        addField(grid, 4, "Observación", observacion);

        VBox content = new VBox(7, title, stock, hint, grid, error);
        content.setPadding(new Insets(8));
        content.setPrefWidth(500);
        return content;
    }

    private void addField(GridPane grid, int row, String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        grid.add(label, 0, row * 2);
        grid.add(field, 0, row * 2 + 1);
        GridPane.setFillWidth(field, true);
    }

    private void validar() {
        double value = parseCantidad();
        if (value <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero.");
        }
        if (producto.unidadMedida() == UnidadMedida.UN && value != Math.rint(value)) {
            throw new IllegalArgumentException("Este producto se vende por unidad; ingresá una cantidad entera.");
        }
        if (salida.isSelected() && value > producto.stockActual()) {
            throw new IllegalArgumentException("La salida supera el stock disponible.");
        }
        if (motivoActual().isBlank()) {
            throw new IllegalArgumentException("Indicá el motivo del movimiento.");
        }
    }

    private double parseCantidad() {
        String value = cantidad.getText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ingresá la cantidad.");
        }
        String normalized = value.trim().replace(" ", "").replace(",", ".");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Revisá la cantidad ingresada.");
        }
    }

    private String motivoActual() {
        String editorText = motivo.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        return motivo.getValue() == null ? "" : motivo.getValue().trim();
    }

    private String formatStock(Producto value) {
        String number = BigDecimal.valueOf(value.stockActual()).stripTrailingZeros().toPlainString();
        return number + (value.unidadMedida() == UnidadMedida.KG ? " kg" : " un.");
    }

    private void applyStyles(DialogPane pane) {
        var css = StockDialog.class.getResource("/styles/app.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
    }

    public record StockForm(
            TipoMovimientoStock tipo,
            double cantidad,
            String motivo,
            String referencia,
            String observacion
    ) {
    }
}
