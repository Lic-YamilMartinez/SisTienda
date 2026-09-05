package py.sistienda.ui.venta;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.VentaService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class VentaView extends HBox {

    private final ProductoService productoService;
    private final VentaService ventaService;
    private final Usuario usuario;
    private final CajaSesion caja;

    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final FilteredList<Producto> filtrados = new FilteredList<>(productos, value -> true);
    private final ObservableList<CartItem> carrito = FXCollections.observableArrayList();

    private final TextField buscar = new TextField();
    private final TableView<Producto> tablaProductos = new TableView<>();
    private final TableView<CartItem> tablaCarrito = new TableView<>();
    private final ComboBox<MetodoPago> metodoPago = new ComboBox<>();
    private final TextField recibido = new TextField();
    private final Label total = new Label("Gs. 0");
    private final Label vuelto = new Label("Gs. 0");
    private final Label feedback = new Label();

    public VentaView(
            ProductoService productoService,
            VentaService ventaService,
            Usuario usuario,
            CajaSesion caja
    ) {
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.usuario = usuario;
        this.caja = caja;

        getStyleClass().add("pos-layout");
        setSpacing(16);
        setPadding(new Insets(0));

        VBox productPanel = buildProductPanel();
        VBox cartPanel = buildCartPanel();
        HBox.setHgrow(productPanel, Priority.ALWAYS);
        productPanel.setMaxWidth(Double.MAX_VALUE);
        cartPanel.setPrefWidth(470);
        cartPanel.setMinWidth(420);

        getChildren().addAll(productPanel, cartPanel);

        configurarFiltros();
        configurarProductos();
        configurarCarrito();
        configurarPago();
        recargarProductos();
        recalcular();
    }

    private VBox buildProductPanel() {
        Label eyebrow = new Label("PUNTO DE VENTA");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Elegí los productos");
        title.getStyleClass().add("pos-section-title");
        Label subtitle = new Label("Buscá y agregá productos al carrito. El stock se descuenta recién al cobrar.");
        subtitle.getStyleClass().add("pos-subtitle");
        subtitle.setWrapText(true);

        buscar.setPromptText("Buscar producto o categoría...");
        buscar.getStyleClass().add("pos-search");

        VBox header = new VBox(5, eyebrow, title, subtitle, buscar);
        header.setPadding(new Insets(18, 18, 12, 18));

        VBox panel = new VBox(0, header, tablaProductos);
        panel.getStyleClass().add("pos-panel");
        VBox.setVgrow(tablaProductos, Priority.ALWAYS);
        return panel;
    }

    private VBox buildCartPanel() {
        Label title = new Label("Venta actual");
        title.getStyleClass().add("pos-section-title");
        Label subtitle = new Label("Revisá cantidades y cobrá cuando esté todo listo.");
        subtitle.getStyleClass().add("pos-subtitle");

        feedback.getStyleClass().add("pos-feedback");
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);

        VBox cartHeader = new VBox(4, title, subtitle, feedback);
        cartHeader.setPadding(new Insets(18, 18, 10, 18));

        metodoPago.getItems().setAll(MetodoPago.values());
        metodoPago.setValue(MetodoPago.EFECTIVO);
        metodoPago.getStyleClass().add("pos-control");
        metodoPago.setMaxWidth(Double.MAX_VALUE);

        recibido.setPromptText("Efectivo recibido");
        recibido.getStyleClass().add("pos-control");

        Label totalLabel = new Label("Total");
        totalLabel.getStyleClass().add("pos-summary-label");
        total.getStyleClass().add("pos-total");

        Label vueltoLabel = new Label("Vuelto");
        vueltoLabel.getStyleClass().add("pos-summary-label");
        vuelto.getStyleClass().add("pos-change");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox totalRow = new HBox(10, totalLabel, spacer, total);
        totalRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox vueltoRow = new HBox(10, vueltoLabel, spacer2, vuelto);
        vueltoRow.setAlignment(Pos.CENTER_LEFT);

        Button cobrar = new Button("Cobrar venta");
        cobrar.getStyleClass().add("pos-pay-button");
        cobrar.setMaxWidth(Double.MAX_VALUE);
        cobrar.setOnAction(event -> cobrar());

        VBox payment = new VBox(8,
                fieldLabel("Método de pago"), metodoPago,
                fieldLabel("Recibido (Gs.)"), recibido,
                totalRow, vueltoRow, cobrar
        );
        payment.getStyleClass().add("pos-payment");
        payment.setPadding(new Insets(14, 18, 18, 18));

        VBox panel = new VBox(0, cartHeader, tablaCarrito, payment);
        panel.getStyleClass().add("pos-panel");
        VBox.setVgrow(tablaCarrito, Priority.ALWAYS);
        return panel;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private void configurarFiltros() {
        buscar.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtrados.setPredicate(producto -> query.isBlank()
                    || producto.nombre().toLowerCase(Locale.ROOT).contains(query)
                    || (producto.categoriaNombre() != null
                    && producto.categoriaNombre().toLowerCase(Locale.ROOT).contains(query)));
        });
    }

    private void configurarProductos() {
        tablaProductos.setItems(filtrados);
        tablaProductos.setPlaceholder(new Label("No hay productos disponibles."));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaProductos.getStyleClass().add("pos-table");

        TableColumn<Producto, Producto> productoCol = new TableColumn<>("Producto");
        productoCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        productoCol.setPrefWidth(260);
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

        TableColumn<Producto, String> priceCol = new TableColumn<>("Precio");
        priceCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().precioVenta())));
        priceCol.setPrefWidth(110);

        TableColumn<Producto, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatStock(cell.getValue())));
        stockCol.setPrefWidth(90);

        TableColumn<Producto, Producto> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(95);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button add = new Button("Agregar");
            {
                add.getStyleClass().add("pos-add-button");
            }
            @Override
            protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                add.setDisable(item.stockActual() <= 0);
                add.setOnAction(event -> pedirCantidad(item, null));
                setAlignment(Pos.CENTER);
                setGraphic(add);
            }
        });

        tablaProductos.getColumns().setAll(productoCol, priceCol, stockCol, actionCol);
    }

    private void configurarCarrito() {
        tablaCarrito.setItems(carrito);
        tablaCarrito.setPlaceholder(new Label("Tu carrito está vacío."));
        tablaCarrito.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaCarrito.getStyleClass().add("pos-table");

        TableColumn<CartItem, String> productCol = new TableColumn<>("Producto");
        productCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto.nombre()));
        productCol.setPrefWidth(150);

        TableColumn<CartItem, String> qtyCol = new TableColumn<>("Cant.");
        qtyCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQuantity(cell.getValue())));
        qtyCol.setPrefWidth(70);

        TableColumn<CartItem, String> subtotalCol = new TableColumn<>("Subtotal");
        subtotalCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().subtotal())));
        subtotalCol.setPrefWidth(100);

        TableColumn<CartItem, CartItem> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(105);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button edit = smallCartButton("Cant.");
            private final Button remove = smallCartButton("Quitar");
            private final HBox box = new HBox(5, edit, remove);
            {
                remove.getStyleClass().add("pos-remove-button");
                box.setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                edit.setOnAction(event -> pedirCantidad(item.producto, item));
                remove.setOnAction(event -> {
                    carrito.remove(item);
                    recalcular();
                });
                setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });

        tablaCarrito.getColumns().setAll(productCol, qtyCol, subtotalCol, actionCol);
    }

    private Button smallCartButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("pos-cart-button");
        return button;
    }

    private void configurarPago() {
        metodoPago.valueProperty().addListener((obs, oldValue, newValue) -> actualizarFormaPago());
        recibido.textProperty().addListener((obs, oldValue, newValue) -> recalcular());
        carrito.addListener((javafx.collections.ListChangeListener<CartItem>) change -> recalcular());
        actualizarFormaPago();
    }

    private void actualizarFormaPago() {
        boolean cash = metodoPago.getValue() == MetodoPago.EFECTIVO;
        recibido.setDisable(!cash);
        if (!cash) {
            recibido.clear();
            recibido.setPromptText("No aplica");
        } else {
            recibido.setPromptText("Efectivo recibido");
        }
        recalcular();
    }

    private void pedirCantidad(Producto producto, CartItem existing) {
        String initial = existing == null
                ? (producto.unidadMedida() == UnidadMedida.UN ? "1" : "0,5")
                : BigDecimal.valueOf(existing.cantidad).stripTrailingZeros().toPlainString();
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(existing == null ? "Agregar producto" : "Cambiar cantidad");
        dialog.setHeaderText(producto.nombre() + " · Stock: " + formatStock(producto));
        dialog.setContentText(producto.unidadMedida() == UnidadMedida.UN ? "Cantidad de unidades:" : "Cantidad en kg:");
        applyDialogStyle(dialog.getDialogPane());

        dialog.showAndWait().ifPresent(value -> ejecutar(() -> {
            double qty = parseQuantity(value);
            if (producto.unidadMedida() == UnidadMedida.UN && Math.abs(qty - Math.rint(qty)) > 0.000001) {
                throw new ValidationException("Este producto se vende por unidad y no acepta decimales.");
            }
            if (qty <= 0) {
                throw new ValidationException("La cantidad debe ser mayor a cero.");
            }
            if (qty > producto.stockActual()) {
                throw new ValidationException("Stock insuficiente. Disponible: " + formatStock(producto));
            }

            if (existing == null) {
                carrito.add(new CartItem(producto, qty));
            } else {
                existing.cantidad = qty;
                tablaCarrito.refresh();
            }
            recalcular();
        }));
    }

    private void cobrar() {
        ejecutar(() -> {
            List<LineaVenta> lineas = carrito.stream()
                    .map(item -> new LineaVenta(item.producto, item.cantidad))
                    .toList();

            double recibidoValue = metodoPago.getValue() == MetodoPago.EFECTIVO
                    ? parseMoneyOrZero(recibido.getText())
                    : 0;

            VentaResultado result = ventaService.vender(
                    usuario,
                    caja,
                    lineas,
                    metodoPago.getValue(),
                    recibidoValue
            );

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Venta registrada");
            success.setHeaderText("Ticket #" + result.nroTicket() + " · " + formatCurrency(result.total()));
            success.setContentText(result.metodoPago() == MetodoPago.EFECTIVO
                    ? "Recibido: " + formatCurrency(result.recibido()) + "\nVuelto: " + formatCurrency(result.vuelto())
                    : "Pago: " + result.metodoPago().descripcion());
            applyDialogStyle(success.getDialogPane());
            success.showAndWait();

            carrito.clear();
            recibido.clear();
            recargarProductos();
            feedback.setText("Venta registrada correctamente. Ticket #" + result.nroTicket());
            feedback.setVisible(true);
            feedback.setManaged(true);
        });
    }

    private void recargarProductos() {
        productos.setAll(productoService.listarActivos());
        tablaProductos.refresh();
    }

    private void recalcular() {
        double totalValue = carrito.stream().mapToDouble(CartItem::subtotal).sum();
        total.setText(formatCurrency(totalValue));

        double recibidoValue = 0;
        if (metodoPago.getValue() == MetodoPago.EFECTIVO) {
            recibidoValue = parseMoneySilently(recibido.getText());
        }
        vuelto.setText(formatCurrency(Math.max(0, recibidoValue - totalValue)));
    }

    private double parseQuantity(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Ingresá una cantidad.");
        }
        try {
            return Double.parseDouble(value.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ValidationException("Ingresá una cantidad válida.");
        }
    }

    private double parseMoneyOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String normalized = normalizeMoney(value);
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el efectivo recibido.");
        }
    }

    private double parseMoneySilently(String value) {
        try {
            return parseMoneyOrZero(value);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private String normalizeMoney(String value) {
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
        return normalized;
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private String formatStock(Producto producto) {
        String qty = BigDecimal.valueOf(producto.stockActual()).stripTrailingZeros().toPlainString();
        return qty + (producto.unidadMedida() == UnidadMedida.KG ? " kg" : " un.");
    }

    private String formatQuantity(CartItem item) {
        String qty = BigDecimal.valueOf(item.cantidad).stripTrailingZeros().toPlainString();
        return qty + (item.producto.unidadMedida() == UnidadMedida.KG ? " kg" : "");
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false);
            feedback.setManaged(false);
            action.run();
        } catch (ValidationException e) {
            showFeedback(e.getMessage());
        } catch (RuntimeException e) {
            Throwable current = e;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            showFeedback(current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage());
        }
    }

    private void showFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void applyDialogStyle(javafx.scene.control.DialogPane pane) {
        var css = VentaView.class.getResource("/styles/app.css");
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
    }

    private static final class CartItem {
        private final Producto producto;
        private double cantidad;

        private CartItem(Producto producto, double cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
        }

        private double subtotal() {
            return producto.precioVenta() * cantidad;
        }
    }
}
