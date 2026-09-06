package py.sistienda.ui.catalogo;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.service.ProductoService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CatalogoConsultaView extends BorderPane {

    private final ProductoService productoService;
    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final FilteredList<Producto> filtrados = new FilteredList<>(productos, item -> true);
    private final TextField buscar = new TextField();
    private final TableView<Producto> tabla = new TableView<>();

    public CatalogoConsultaView(ProductoService productoService) {
        this.productoService = productoService;
        getStyleClass().add("content-area");
        setPadding(new Insets(28, 32, 28, 32));
        setTop(buildHeader());
        setCenter(buildContent());
        configurarTabla();
        configurarFiltro();
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("CATÁLOGO");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Productos & Precios");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Consultá precios, stock disponible y códigos sin acceso a costos ni movimientos de inventario.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        return new VBox(3, eyebrow, title, subtitle);
    }

    private VBox buildContent() {
        buscar.setPromptText("Buscar por producto, categoría, código o PLU...");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(430);

        Label count = new Label();
        count.textProperty().bind(Bindings.size(filtrados).asString("%d productos"));
        count.getStyleClass().add("result-count");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, buscar, spacer, count);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("catalog-toolbar");

        VBox panel = new VBox(0, toolbar, tabla);
        panel.getStyleClass().add("table-panel");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox content = new VBox(16, panel);
        content.setPadding(new Insets(20, 0, 0, 0));
        VBox.setVgrow(panel, Priority.ALWAYS);
        return content;
    }

    private void configurarFiltro() {
        buscar.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtrados.setPredicate(producto -> query.isBlank()
                    || contiene(producto.nombre(), query)
                    || contiene(producto.categoriaNombre(), query)
                    || contiene(producto.codigoBarras(), query)
                    || (producto.pluBalanza() != null && String.valueOf(producto.pluBalanza()).contains(query)));
        });
    }

    private void configurarTabla() {
        tabla.setItems(filtrados);
        tabla.setPlaceholder(new Label("No hay productos disponibles."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getStyleClass().add("catalog-table");

        TableColumn<Producto, Producto> productoCol = new TableColumn<>("Producto");
        productoCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        productoCol.setPrefWidth(330);
        productoCol.setCellFactory(column -> new TableCell<>() {
            private final Label name = new Label();
            private final Label category = new Label();
            private final VBox box = new VBox(2, name, category);
            {
                name.getStyleClass().add("product-name");
                category.getStyleClass().add("product-category");
            }
            @Override
            protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                name.setText(item.nombre());
                category.setText(item.categoriaNombre() == null ? "Sin categoría" : item.categoriaNombre());
                setGraphic(box);
            }
        });

        TableColumn<Producto, String> ventaCol = new TableColumn<>("Venta");
        ventaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().unidadMedida() == UnidadMedida.KG ? "Por kg" : "Unidad"));
        ventaCol.setPrefWidth(95);

        TableColumn<Producto, String> precioCol = new TableColumn<>("Precio");
        precioCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().precioVenta())));
        precioCol.setPrefWidth(135);

        TableColumn<Producto, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatStock(cell.getValue())));
        stockCol.setPrefWidth(120);

        TableColumn<Producto, String> codigoCol = new TableColumn<>("Código / PLU");
        codigoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(identificacion(cell.getValue())));
        codigoCol.setPrefWidth(190);

        tabla.getColumns().setAll(productoCol, ventaCol, precioCol, stockCol, codigoCol);
    }

    private void recargar() {
        productos.setAll(productoService.listarActivos());
    }

    private boolean contiene(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String identificacion(Producto producto) {
        if (producto.codigoBarras() != null && !producto.codigoBarras().isBlank()) {
            return producto.codigoBarras();
        }
        if (producto.pluBalanza() != null) {
            return "PLU " + String.format("%05d", producto.pluBalanza());
        }
        return "—";
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private String formatStock(Producto producto) {
        String qty = BigDecimal.valueOf(producto.stockActual()).stripTrailingZeros().toPlainString();
        return qty + (producto.unidadMedida() == UnidadMedida.KG ? " kg" : " un.");
    }
}
