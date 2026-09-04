package py.sistienda.ui.catalogo;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CategoriaProducto;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.service.CategoriaService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.StockService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class CatalogoView extends BorderPane {

    private static final String TODAS_LAS_CATEGORIAS = "Todas las categorías";

    private final CategoriaService categoriaService;
    private final ProductoService productoService;
    private final StockService stockService;

    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final FilteredList<Producto> productosFiltrados = new FilteredList<>(productos, value -> true);
    private final ObservableList<CategoriaProducto> categorias = FXCollections.observableArrayList();

    private final TextField buscar = new TextField();
    private final ComboBox<String> filtroCategoria = new ComboBox<>();
    private final TableView<Producto> tabla = new TableView<>();

    private final Label totalProductos = new Label("0");
    private final Label sinStock = new Label("0");
    private final Label valorInventario = new Label("Gs. 0");
    private final Label totalCategorias = new Label("0");
    private final Label feedback = new Label();

    public CatalogoView(CategoriaService categoriaService, ProductoService productoService, StockService stockService) {
        this.categoriaService = categoriaService;
        this.productoService = productoService;
        this.stockService = stockService;

        getStyleClass().add("content-area");
        setPadding(new Insets(28, 32, 28, 32));
        setTop(buildHeader());
        setCenter(buildWorkspace());

        configurarFiltros();
        configurarTabla();
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("INVENTARIO");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Catálogo & Stock");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Administrá tus productos, precios y existencias desde un solo lugar.");
        subtitle.getStyleClass().add("page-subtitle");

        Button nuevaCategoria = new Button("+ Categoría");
        nuevaCategoria.getStyleClass().add("secondary-button");
        nuevaCategoria.setOnAction(event -> crearCategoria());

        Button nuevoProducto = new Button("+ Nuevo producto");
        nuevoProducto.getStyleClass().add("primary-button");
        nuevoProducto.setOnAction(event -> editarProducto(null));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox titleRow = new HBox(14, new VBox(2, eyebrow, title, subtitle), spacer, nuevaCategoria, nuevoProducto);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        HBox cards = new HBox(14,
                metricCard("Productos activos", totalProductos, "Disponibles en catálogo"),
                metricCard("Sin stock", sinStock, "Requieren reposición"),
                metricCard("Valor de inventario", valorInventario, "Calculado al costo"),
                metricCard("Categorías", totalCategorias, "Activas")
        );
        cards.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox header = new VBox(18, titleRow, feedback, cards);
        header.setPadding(new Insets(0, 0, 20, 0));
        return header;
    }

    private VBox metricCard(String label, Label value, String hint) {
        Label title = new Label(label);
        title.getStyleClass().add("metric-title");
        value.getStyleClass().add("metric-value");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("metric-hint");

        VBox card = new VBox(6, title, value, hintLabel);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(16, 18, 15, 18));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox buildWorkspace() {
        buscar.setPromptText("Buscar por nombre o categoría...");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(330);

        filtroCategoria.setPrefWidth(210);
        filtroCategoria.getStyleClass().add("filter-combo");

        Label count = new Label();
        count.textProperty().bind(productosFiltrados.sizeProperty().asString("%d productos"));
        count.getStyleClass().add("result-count");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, buscar, filtroCategoria, spacer, count);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("catalog-toolbar");

        VBox panel = new VBox(0, toolbar, tabla);
        panel.getStyleClass().add("table-panel");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return panel;
    }

    private void configurarFiltros() {
        buscar.textProperty().addListener((observable, oldValue, newValue) -> aplicarFiltro());
        filtroCategoria.valueProperty().addListener((observable, oldValue, newValue) -> aplicarFiltro());
    }

    private void configurarTabla() {
        tabla.setItems(productosFiltrados);
        tabla.setPlaceholder(new Label("Todavía no hay productos. Creá el primero con “+ Nuevo producto”."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.getStyleClass().add("catalog-table");

        TableColumn<Producto, Producto> productoColumn = new TableColumn<>("Producto");
        productoColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        productoColumn.setPrefWidth(280);
        productoColumn.setCellFactory(column -> new TableCell<>() {
            private final Label name = new Label();
            private final Label category = new Label();
            private final VBox box = new VBox(3, name, category);
            {
                name.getStyleClass().add("product-name");
                category.getStyleClass().add("product-category");
            }

            @Override
            protected void updateItem(Producto value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                } else {
                    name.setText(value.nombre());
                    category.setText(value.categoriaNombre() == null ? "Sin categoría" : value.categoriaNombre());
                    setGraphic(box);
                }
            }
        });

        TableColumn<Producto, String> unidadColumn = new TableColumn<>("Venta");
        unidadColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().unidadMedida() == UnidadMedida.KG ? "Por kg" : "Unidad"));
        unidadColumn.setPrefWidth(90);

        TableColumn<Producto, String> precioColumn = new TableColumn<>("Precio");
        precioColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().precioVenta())));
        precioColumn.setPrefWidth(120);

        TableColumn<Producto, String> costoColumn = new TableColumn<>("Costo");
        costoColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().costo())));
        costoColumn.setPrefWidth(120);

        TableColumn<Producto, String> stockColumn = new TableColumn<>("Stock");
        stockColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatStock(cell.getValue())));
        stockColumn.setPrefWidth(105);
        stockColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("stock-zero", "stock-positive");
                if (!empty && getTableRow() != null && getTableRow().getItem() instanceof Producto producto) {
                    getStyleClass().add(producto.stockActual() <= 0 ? "stock-zero" : "stock-positive");
                }
            }
        });

        TableColumn<Producto, Producto> estadoColumn = new TableColumn<>("Estado");
        estadoColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        estadoColumn.setPrefWidth(110);
        estadoColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(Producto value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                badge.getStyleClass().removeAll("status-ok", "status-empty");
                if (value.stockActual() <= 0) {
                    badge.setText("Sin stock");
                    badge.getStyleClass().add("status-empty");
                } else {
                    badge.setText("Disponible");
                    badge.getStyleClass().add("status-ok");
                }
                setGraphic(badge);
            }
        });

        TableColumn<Producto, Producto> actionsColumn = new TableColumn<>("Acciones");
        actionsColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionsColumn.setPrefWidth(235);
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button edit = smallButton("Editar");
            private final Button stock = smallButton("Stock +/-");
            private final Button disable = smallButton("Desactivar");
            private final HBox box = new HBox(7, edit, stock, disable);

            {
                stock.getStyleClass().add("stock-action-button");
                disable.getStyleClass().add("danger-link-button");
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Producto value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                edit.setOnAction(event -> editarProducto(value));
                stock.setOnAction(event -> moverStock(value));
                disable.setOnAction(event -> desactivarProducto(value));
                setGraphic(box);
            }
        });

        tabla.getColumns().setAll(productoColumn, unidadColumn, precioColumn, costoColumn,
                stockColumn, estadoColumn, actionsColumn);
    }

    private Button smallButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("table-action-button");
        return button;
    }

    private void aplicarFiltro() {
        String query = buscar.getText() == null ? "" : buscar.getText().trim().toLowerCase(Locale.ROOT);
        String category = filtroCategoria.getValue();

        productosFiltrados.setPredicate(producto -> {
            boolean textMatches = query.isBlank()
                    || producto.nombre().toLowerCase(Locale.ROOT).contains(query)
                    || (producto.categoriaNombre() != null
                    && producto.categoriaNombre().toLowerCase(Locale.ROOT).contains(query));

            boolean categoryMatches = category == null
                    || TODAS_LAS_CATEGORIAS.equals(category)
                    || category.equals(producto.categoriaNombre());

            return textMatches && categoryMatches;
        });
    }

    private void crearCategoria() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Nueva categoría");
        dialog.setHeaderText("Organizá mejor tu catálogo");
        dialog.setContentText("Nombre de la categoría:");
        applyDialogStyles(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(nombre -> ejecutar(() -> {
            categoriaService.crear(nombre);
            recargar();
            mostrarFeedback("Categoría creada correctamente.");
        }));
    }

    private void editarProducto(Producto producto) {
        ProductoDialog dialog = new ProductoDialog(getScene().getWindow(), List.copyOf(categorias), producto);
        dialog.showAndWait().ifPresent(form -> ejecutar(() -> {
            CategoriaProducto category = form.categoria();
            if (producto == null) {
                productoService.crear(
                        form.nombre(),
                        category == null ? null : category.id(),
                        category == null ? null : category.nombre(),
                        form.unidadMedida(),
                        form.precioVenta(),
                        form.costo()
                );
                mostrarFeedback("Producto creado. Ahora podés registrar su stock inicial con “Stock +/-”.");
            } else {
                productoService.actualizar(
                        producto,
                        form.nombre(),
                        category == null ? null : category.id(),
                        category == null ? null : category.nombre(),
                        form.unidadMedida(),
                        form.precioVenta(),
                        form.costo()
                );
                mostrarFeedback("Producto actualizado correctamente.");
            }
            recargar();
        }));
    }

    private void moverStock(Producto producto) {
        StockDialog dialog = new StockDialog(getScene().getWindow(), producto);
        dialog.showAndWait().ifPresent(form -> ejecutar(() -> {
            stockService.registrar(
                    producto,
                    form.tipo(),
                    form.motivo(),
                    form.cantidad(),
                    form.referencia(),
                    form.observacion()
            );
            recargar();
            mostrarFeedback("Stock actualizado correctamente.");
        }));
    }

    private void desactivarProducto(Producto producto) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(getScene().getWindow());
        confirmation.setTitle("Desactivar producto");
        confirmation.setHeaderText("¿Desactivar “" + producto.nombre() + "”?");
        confirmation.setContentText("Dejará de aparecer en el catálogo, pero sus movimientos históricos se conservan.");
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType accept = new ButtonType("Desactivar", ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(cancel, accept);
        applyDialogStyles(confirmation.getDialogPane());

        if (confirmation.showAndWait().orElse(cancel) == accept) {
            ejecutar(() -> {
                productoService.desactivar(producto.id());
                recargar();
                mostrarFeedback("Producto desactivado.");
            });
        }
    }

    private void recargar() {
        ejecutarSilencioso(() -> {
            categorias.setAll(categoriaService.listarActivas());
            productos.setAll(productoService.listarActivos());

            String selected = filtroCategoria.getValue();
            filtroCategoria.getItems().setAll(TODAS_LAS_CATEGORIAS);
            categorias.stream().map(CategoriaProducto::nombre).forEach(filtroCategoria.getItems()::add);
            if (selected != null && filtroCategoria.getItems().contains(selected)) {
                filtroCategoria.setValue(selected);
            } else {
                filtroCategoria.setValue(TODAS_LAS_CATEGORIAS);
            }

            actualizarMetricas();
            aplicarFiltro();
        });
    }

    private void actualizarMetricas() {
        totalProductos.setText(String.valueOf(productos.size()));
        long empty = productos.stream().filter(producto -> producto.stockActual() <= 0).count();
        sinStock.setText(String.valueOf(empty));
        totalCategorias.setText(String.valueOf(categorias.size()));

        double inventoryValue = productos.stream()
                .mapToDouble(producto -> producto.costo() * producto.stockActual())
                .sum();
        valorInventario.setText(formatCurrency(inventoryValue));
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private String formatStock(Producto producto) {
        String number = BigDecimal.valueOf(producto.stockActual()).stripTrailingZeros().toPlainString();
        return number + (producto.unidadMedida() == UnidadMedida.KG ? " kg" : " un.");
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void ejecutar(Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            mostrarError("Revisá los datos", e.getMessage(), Alert.AlertType.WARNING);
        } catch (RuntimeException e) {
            mostrarError("No pudimos completar la operación", mensajeRaiz(e), Alert.AlertType.ERROR);
        }
    }

    private void ejecutarSilencioso(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            mostrarError("No pudimos cargar el catálogo", mensajeRaiz(e), Alert.AlertType.ERROR);
        }
    }

    private String mensajeRaiz(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "Ocurrió un error inesperado." : message;
    }

    private void mostrarError(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.initOwner(getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        applyDialogStyles(alert.getDialogPane());
        alert.showAndWait();
    }

    private void applyDialogStyles(javafx.scene.control.DialogPane pane) {
        var css = CatalogoView.class.getResource("/styles/app.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
    }
}
