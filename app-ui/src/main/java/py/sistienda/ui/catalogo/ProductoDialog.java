package py.sistienda.ui.catalogo;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
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
    private final TextField codigoBarras = new TextField();
    private final TextField pluBalanza = new TextField();
    private final Label identificacionHint = new Label();
    private final Label error = new Label();

    public ProductoDialog(Window owner, List<CategoriaProducto> categorias, Producto producto) {
        initOwner(owner);
        setTitle(producto == null ? "Nuevo producto" : "Editar producto");
        setHeaderText(null);

        categoria.getItems().add(SIN_CATEGORIA);
        categoria.getItems().addAll(categorias);
        categoria.setConverter(new StringConverter<>() {
            @Override public String toString(CategoriaProducto value) { return value == null ? "Sin categoría" : value.nombre(); }
            @Override public CategoriaProducto fromString(String value) { return null; }
        });
        categoria.setValue(SIN_CATEGORIA);
        unidad.getItems().setAll(UnidadMedida.values());

        nombre.setPromptText("Ej.: Coca Cola 2L");
        categoria.setPromptText("Seleccionar categoría");
        unidad.setPromptText("Cómo se vende");
        precioVenta.setPromptText("Ej.: 15000");
        costo.setPromptText("Ej.: 11000");
        codigoBarras.setPromptText("Escaneá o dejá vacío para generar código interno");
        pluBalanza.setPromptText("PLU 0 a 99999 · vacío = automático");

        for (Control control : new Control[]{nombre, categoria, unidad, precioVenta, costo, codigoBarras, pluBalanza}) {
            control.getStyleClass().add("form-control");
        }
        identificacionHint.getStyleClass().add("dialog-subtitle");
        identificacionHint.setWrapText(true);
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        if (producto != null) {
            nombre.setText(producto.nombre());
            if (producto.categoriaId() != null) {
                categoria.getItems().stream().filter(item -> item.id() == producto.categoriaId())
                        .findFirst().ifPresent(categoria::setValue);
            }
            unidad.setValue(producto.unidadMedida());
            precioVenta.setText(formatInput(producto.precioVenta()));
            costo.setText(formatInput(producto.costo()));
            codigoBarras.setText(producto.codigoBarras() == null ? "" : producto.codigoBarras());
            pluBalanza.setText(producto.pluBalanza() == null ? "" : String.valueOf(producto.pluBalanza()));
            if (producto.stockActual() != 0d) {
                unidad.setDisable(true);
                unidad.setTooltip(new Tooltip("Para cambiar la unidad de venta, primero dejá el stock en cero."));
            }
        } else {
            unidad.setValue(UnidadMedida.UN);
        }

        unidad.valueProperty().addListener((obs, oldValue, newValue) -> actualizarIdentificacionHint());
        actualizarIdentificacionHint();

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
            if (button != GUARDAR) return null;
            return new ProductoForm(
                    nombre.getText().trim(), categoriaSeleccionada(), unidad.getValue(),
                    parseNumero(precioVenta.getText(), "precio de venta"),
                    parseNumero(costo.getText(), "costo"),
                    codigoBarras.getText() == null ? null : codigoBarras.getText().trim(),
                    parsePluOpcional(pluBalanza.getText())
            );
        });
    }

    private VBox buildContent(boolean nuevo) {
        Label title = new Label(nuevo ? "Agregar producto" : "Actualizar producto");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label(nuevo
                ? "Cargá los datos comerciales. SisTienda puede generar la identificación interna automáticamente."
                : "Actualizá datos comerciales, código y PLU. El stock no se modifica desde esta pantalla.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("dialog-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        grid.setPadding(new Insets(12, 0, 0, 0));
        addField(grid, 0, "Nombre del producto", nombre);
        addField(grid, 1, "Categoría", categoria);
        addField(grid, 2, "Unidad de venta", unidad);
        addField(grid, 3, "Precio de venta (Gs.)", precioVenta);
        addField(grid, 4, "Costo (Gs.)", costo);
        addField(grid, 5, "Código de barras", codigoBarras);
        addField(grid, 6, "PLU de balanza", pluBalanza);

        VBox content = new VBox(8, title, subtitle, grid, identificacionHint, error);
        content.setPadding(new Insets(8));
        content.setPrefWidth(520);
        return content;
    }

    private void actualizarIdentificacionHint() {
        boolean kg = unidad.getValue() == UnidadMedida.KG;
        pluBalanza.setDisable(!kg);
        identificacionHint.setText(kg
                ? "Producto pesable: el PLU identifica el artículo en la etiqueta de balanza. Si queda vacío, SisTienda asigna uno automáticamente."
                : "Producto por unidad: podés escanear el código del fabricante. Si queda vacío, SisTienda genera un EAN-13 interno.");
    }

    private void addField(GridPane grid, int row, String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        grid.add(label, 0, row * 2);
        grid.add(field, 0, row * 2 + 1);
        GridPane.setFillWidth(field, true);
    }

    private void validar() {
        if (nombre.getText() == null || nombre.getText().isBlank()) throw new IllegalArgumentException("Ingresá el nombre del producto.");
        if (unidad.getValue() == null) throw new IllegalArgumentException("Seleccioná si el producto se vende por unidad o por kilogramo.");
        double precio = parseNumero(precioVenta.getText(), "precio de venta");
        double costoValue = parseNumero(costo.getText(), "costo");
        if (precio < 0 || costoValue < 0) throw new IllegalArgumentException("Precio y costo no pueden ser negativos.");
        String code = codigoBarras.getText() == null ? "" : codigoBarras.getText().trim();
        if (!code.isBlank() && !code.matches("[A-Za-z0-9._-]{3,64}")) {
            throw new IllegalArgumentException("Revisá el código de barras: sólo letras, números, punto, guion o guion bajo.");
        }
        parsePluOpcional(pluBalanza.getText());
    }

    private CategoriaProducto categoriaSeleccionada() {
        CategoriaProducto selected = categoria.getValue();
        return selected == null || selected.id() == SIN_CATEGORIA.id() ? null : selected;
    }

    private Integer parsePluOpcional(String value) {
        if (unidad.getValue() != UnidadMedida.KG || value == null || value.isBlank()) return null;
        try {
            int plu = Integer.parseInt(value.trim());
            if (plu < 0 || plu > 99_999) throw new NumberFormatException();
            return plu;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El PLU debe ser un número entre 0 y 99999.");
        }
    }

    private double parseNumero(String value, String campo) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Ingresá el " + campo + ".");
        String normalized = value.trim().replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
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
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }

    public record ProductoForm(
            String nombre,
            CategoriaProducto categoria,
            UnidadMedida unidadMedida,
            double precioVenta,
            double costo,
            String codigoBarras,
            Integer pluBalanza
    ) {
    }
}
