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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import py.sistienda.core.model.CategoriaProducto;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;

import java.math.BigDecimal;
import java.util.List;

public final class ProductoDialog extends Dialog<ProductoDialog.ProductoForm> {

    private static final ButtonType GUARDAR = new ButtonType("Guardar producto", ButtonBar.ButtonData.OK_DONE);
    private static final CategoriaProducto SIN_CATEGORIA = new CategoriaProducto(0L, "Sin categoría", true);

    private final TextField nombre = new TextField();
    private final ComboBox<CategoriaProducto> categoria = new ComboBox<>();
    private final ComboBox<UnidadMedida> unidad = new ComboBox<>();
    private final TextField precioVenta = new TextField();
    private final TextField costo = new TextField();
    private final Label error = new Label();

    public ProductoDialog(Window owner, List<CategoriaProducto> categorias, Producto producto) {
        initOwner(owner);
        setTitle(producto == null ? "Nuevo producto" : "Editar producto");
        setHeaderText(null);

        categoria.getItems().add(SIN_CATEGORIA);
        categoria.getItems().addAll(categorias);
        categoria.setConverter(new StringConverter<>() {
            @Override
            public String toString(CategoriaProducto value) {
                return value == null ? "Sin categoría" : value.nombre();
            }

            @Override
            public CategoriaProducto fromString(String value) {
                return null;
            }
        });
        categoria.setValue(SIN_CATEGORIA);
        unidad.getItems().setAll(UnidadMedida.values());

        nombre.setPromptText("Ej.: Coca Cola 2L");
        categoria.setPromptText("Seleccionar categoría");
        unidad.setPromptText("Cómo se vende");
        precioVenta.setPromptText("Ej.: 15000");
        costo.setPromptText("Ej.: 11000");

        nombre.getStyleClass().add("form-control");
        categoria.getStyleClass().add("form-control");
        unidad.getStyleClass().add("form-control");
        precioVenta.getStyleClass().add("form-control");
        costo.getStyleClass().add("form-control");
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        if (producto != null) {
            nombre.setText(producto.nombre());
            if (producto.categoriaId() != null) {
                categoria.getItems().stream()
                        .filter(item -> item.id() == producto.categoriaId())
                        .findFirst()
                        .ifPresent(categoria::setValue);
            }
            unidad.setValue(producto.unidadMedida());
            precioVenta.setText(formatInput(producto.precioVenta()));
            costo.setText(formatInput(producto.costo()));

            if (producto.stockActual() != 0d) {
                unidad.setDisable(true);
                unidad.setTooltip(new Tooltip("Para cambiar la unidad de venta, primero dejá el stock en cero."));
            }
        } else {
            unidad.setValue(UnidadMedida.UN);
        }

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().addAll(GUARDAR, ButtonType.CANCEL);
        pane.setContent(buildContent(producto == null));
        pane.getStyleClass().add("product-dialog");
        applyStyles(pane);

        Node saveButton = pane.lookupButton(GUARDAR);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validar();
                error.setText("");
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        setResultConverter(button -> {
            if (button != GUARDAR) {
                return null;
            }
            return new ProductoForm(
                    nombre.getText().trim(),
                    categoriaSeleccionada(),
                    unidad.getValue(),
                    parseNumero(precioVenta.getText(), "precio de venta"),
                    parseNumero(costo.getText(), "costo")
            );
        });
    }

    private VBox buildContent(boolean nuevo) {
        Label title = new Label(nuevo ? "Agregar producto" : "Actualizar producto");
        title.getStyleClass().add("dialog-title");

        Label subtitle = new Label(nuevo
                ? "Cargá los datos principales. El stock se registra después como movimiento para conservar el historial."
                : "Actualizá los datos comerciales. El stock no se modifica desde esta pantalla.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("dialog-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(9);
        grid.setPadding(new Insets(12, 0, 0, 0));

        addField(grid, 0, "Nombre del producto", nombre);
        addField(grid, 1, "Categoría", categoria);
        addField(grid, 2, "Unidad de venta", unidad);
        addField(grid, 3, "Precio de venta (Gs.)", precioVenta);
        addField(grid, 4, "Costo (Gs.)", costo);

        VBox content = new VBox(8, title, subtitle, grid, error);
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
        if (nombre.getText() == null || nombre.getText().isBlank()) {
            throw new IllegalArgumentException("Ingresá el nombre del producto.");
        }
        if (unidad.getValue() == null) {
            throw new IllegalArgumentException("Seleccioná si el producto se vende por unidad o por kilogramo.");
        }
        double precio = parseNumero(precioVenta.getText(), "precio de venta");
        double costoValue = parseNumero(costo.getText(), "costo");
        if (precio < 0 || costoValue < 0) {
            throw new IllegalArgumentException("Precio y costo no pueden ser negativos.");
        }
    }

    private CategoriaProducto categoriaSeleccionada() {
        CategoriaProducto selected = categoria.getValue();
        return selected == null || selected.id() == SIN_CATEGORIA.id() ? null : selected;
    }

    private double parseNumero(String value, String campo) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ingresá el " + campo + ".");
        }

        String normalized = value.trim()
                .replace("Gs.", "")
                .replace("Gs", "")
                .replace("₲", "")
                .replace(" ", "");

        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) {
            normalized = normalized.replace(".", "");
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Revisá el " + campo + ". Usá sólo números.");
        }
    }

    private String formatInput(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private void applyStyles(DialogPane pane) {
        var css = ProductoDialog.class.getResource("/styles/app.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
    }

    public record ProductoForm(
            String nombre,
            CategoriaProducto categoria,
            UnidadMedida unidadMedida,
            double precioVenta,
            double costo
    ) {
    }
}
