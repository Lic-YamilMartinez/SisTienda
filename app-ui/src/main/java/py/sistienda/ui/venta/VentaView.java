package py.sistienda.ui.venta;

import py.sistienda.ui.common.UserErrorMessages;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.service.CodigoBarrasService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.VentaService;
import py.sistienda.ui.common.MoneyFieldSupport;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public final class VentaView extends HBox {

    private final ProductoService productoService;
    private final VentaService ventaService;
    private final CodigoBarrasService codigoBarrasService;
    private final String prefijoPeso;
    private final Usuario usuario;
    private final CajaSesion caja;
    private final Consumer<VentaResultado> onVentaRegistrada;

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
    private final Label creditHint = new Label("Seleccionaremos el cliente al registrar la venta. El importe quedará pendiente en su cuenta.");

    public VentaView(ProductoService productoService, VentaService ventaService, Usuario usuario, CajaSesion caja) {
        this(productoService, ventaService, usuario, caja, result -> { }, new CodigoBarrasService(), "20");
    }

    public VentaView(ProductoService productoService, VentaService ventaService, Usuario usuario,
                     CajaSesion caja, Runnable onVentaRegistrada) {
        this(productoService, ventaService, usuario, caja,
                result -> { if (onVentaRegistrada != null) onVentaRegistrada.run(); },
                new CodigoBarrasService(), "20");
    }

    public VentaView(ProductoService productoService, VentaService ventaService, Usuario usuario,
                     CajaSesion caja, Runnable onVentaRegistrada, CodigoBarrasService codigoBarrasService,
                     String prefijoPeso) {
        this(productoService, ventaService, usuario, caja,
                result -> { if (onVentaRegistrada != null) onVentaRegistrada.run(); },
                codigoBarrasService, prefijoPeso);
    }

    public VentaView(ProductoService productoService, VentaService ventaService, Usuario usuario,
                     CajaSesion caja, Consumer<VentaResultado> onVentaRegistrada,
                     CodigoBarrasService codigoBarrasService, String prefijoPeso) {
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.usuario = usuario;
        this.caja = caja;
        this.onVentaRegistrada = onVentaRegistrada == null ? result -> { } : onVentaRegistrada;
        this.codigoBarrasService = codigoBarrasService == null ? new CodigoBarrasService() : codigoBarrasService;
        this.prefijoPeso = prefijoPeso == null || !prefijoPeso.matches("2\\d") ? "20" : prefijoPeso;

        getStyleClass().add("pos-layout");
        setSpacing(12);
        setPadding(Insets.EMPTY);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);

        VBox productPanel = buildProductPanel();
        VBox cartPanel = buildCartPanel();
        HBox.setHgrow(productPanel, Priority.ALWAYS);
        productPanel.setMaxWidth(Double.MAX_VALUE);
        productPanel.setMaxHeight(Double.MAX_VALUE);
        productPanel.setMinHeight(0);
        cartPanel.setPrefWidth(460);
        cartPanel.setMinWidth(390);
        cartPanel.setMaxWidth(530);
        cartPanel.setMaxHeight(Double.MAX_VALUE);
        cartPanel.setMinHeight(0);
        getChildren().addAll(productPanel, cartPanel);

        configurarFiltros();
        configurarProductos();
        configurarCarrito();
        configurarPago();
        recargarProductos();
        recalcular();
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) javafx.application.Platform.runLater(buscar::requestFocus);
        });
    }

    private VBox buildProductPanel() {
        Label eyebrow = new Label("PUNTO DE VENTA");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Productos");
        title.getStyleClass().add("pos-section-title");
        Label scanHint = new Label("Escaneá código/etiqueta o buscá por ID o nombre");
        scanHint.getStyleClass().add("pos-scan-hint");
        VBox heading = new VBox(1, eyebrow, title, scanHint);

        buscar.setPromptText("Escanear código o buscar por ID/nombre...");
        buscar.getStyleClass().add("pos-search");
        buscar.setPrefWidth(360);
        buscar.setMinWidth(210);
        buscar.setOnAction(event -> procesarEntradaRapida());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, heading, spacer, buscar);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(9, 14, 8, 14));

        tablaProductos.setMinHeight(100);
        VBox panel = new VBox(0, header, tablaProductos);
        panel.getStyleClass().add("pos-panel");
        VBox.setVgrow(tablaProductos, Priority.ALWAYS);
        panel.setMinHeight(0);
        return panel;
    }

    private VBox buildCartPanel() {
        Label title = new Label("Venta actual");
        title.getStyleClass().add("pos-section-title");
        feedback.getStyleClass().add("pos-feedback");
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);
        VBox cartHeader = new VBox(4, title, feedback);
        cartHeader.setPadding(new Insets(9, 14, 6, 14));

        metodoPago.getItems().setAll(
                MetodoPago.EFECTIVO,
                MetodoPago.TARJETA,
                MetodoPago.TRANSFERENCIA,
                MetodoPago.MIXTO
        );
        if (ventaService.puedeFiado(usuario)) metodoPago.getItems().add(MetodoPago.FIADO);
        metodoPago.setValue(MetodoPago.EFECTIVO);
        metodoPago.getStyleClass().add("pos-control");
        metodoPago.setMinWidth(180);
        metodoPago.setPrefWidth(210);
        metodoPago.setMaxWidth(Double.MAX_VALUE);

        recibido.setPromptText("Efectivo recibido");
        recibido.getStyleClass().add("pos-control");
        recibido.setMinWidth(150);
        recibido.setMaxWidth(Double.MAX_VALUE);
        MoneyFieldSupport.install(recibido);

        VBox paymentMethod = compactPaymentField("Pago", metodoPago);
        paymentMethod.setMinWidth(180);
        VBox receivedField = compactPaymentField("Recibido (Gs.)", recibido);
        HBox.setHgrow(paymentMethod, Priority.ALWAYS);
        HBox.setHgrow(receivedField, Priority.ALWAYS);
        HBox paymentFields = new HBox(8, paymentMethod, receivedField);

        creditHint.getStyleClass().add("pos-credit-hint");
        creditHint.setWrapText(true);
        creditHint.setVisible(false);
        creditHint.setManaged(false);

        Button cuentas = new Button("Clientes & Fiado");
        cuentas.getStyleClass().add("pos-credit-button");
        boolean puedeFiado = ventaService.puedeFiado(usuario);
        cuentas.setOnAction(event -> ClientesFiadoDialog.gestionar(
                getScene() == null ? null : getScene().getWindow(), ventaService, usuario, caja
        ));

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox creditActions = new HBox(8, cuentas, actionSpacer);
        creditActions.setAlignment(Pos.CENTER_LEFT);
        if (puedeFiado) {
            creditActions.visibleProperty().bind(
                    metodoPago.valueProperty().isEqualTo(MetodoPago.FIADO)
                            .or(metodoPago.valueProperty().isEqualTo(MetodoPago.MIXTO))
            );
            creditActions.managedProperty().bind(creditActions.visibleProperty());
        } else {
            creditActions.setVisible(false);
            creditActions.setManaged(false);
        }

        VBox totalBlock = summaryBlock("TOTAL", total, "pos-total");
        VBox changeBlock = summaryBlock("VUELTO", vuelto, "pos-change");
        HBox.setHgrow(totalBlock, Priority.ALWAYS);
        HBox.setHgrow(changeBlock, Priority.ALWAYS);

        Button cobrar = new Button("Cobrar venta");
        cobrar.setId("pos-pay-action");
        cobrar.getStyleClass().add("pos-pay-button");
        cobrar.setMinWidth(138);
        cobrar.setPrefWidth(148);
        cobrar.setPrefHeight(58);
        cobrar.setMaxHeight(Double.MAX_VALUE);
        cobrar.setOnAction(event -> cobrar());

        HBox summaryAndAction = new HBox(8, totalBlock, changeBlock, cobrar);
        summaryAndAction.setAlignment(Pos.CENTER);

        VBox payment = new VBox(6, paymentFields, creditHint, creditActions, summaryAndAction);
        payment.getStyleClass().add("pos-payment");
        payment.setPadding(new Insets(6, 12, 8, 12));
        payment.setMinHeight(Region.USE_PREF_SIZE);
        payment.setMaxHeight(Region.USE_PREF_SIZE);

        // La lista de la venta gana espacio vertical, pero puede reducirse en pantallas bajas.
        // El bloque de pago conserva siempre su tamaño y queda visible debajo del carrito.
        tablaCarrito.setMinHeight(105);
        tablaCarrito.setPrefHeight(235);
        tablaCarrito.setMaxHeight(300);

        VBox panel = new VBox(0, cartHeader, tablaCarrito, payment);
        panel.getStyleClass().add("pos-panel");
        VBox.setVgrow(tablaCarrito, Priority.ALWAYS);
        panel.setMinHeight(0);
        return panel;
    }

    private VBox compactPaymentField(String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("pos-field-label");
        VBox field = new VBox(3, label, control);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private VBox summaryBlock(String labelText, Label value, String valueStyle) {
        Label label = new Label(labelText);
        label.getStyleClass().add("pos-summary-label");
        value.getStyleClass().add(valueStyle);
        VBox block = new VBox(2, label, value);
        block.getStyleClass().add("pos-summary-block");
        block.setMaxWidth(Double.MAX_VALUE);
        return block;
    }

    private void configurarFiltros() {
        buscar.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtrados.setPredicate(producto -> query.isBlank()
                    || String.valueOf(producto.id()).contains(query)
                    || producto.nombre().toLowerCase(Locale.ROOT).contains(query)
                    || (producto.categoriaNombre() != null && producto.categoriaNombre().toLowerCase(Locale.ROOT).contains(query))
                    || (producto.codigoBarras() != null && producto.codigoBarras().toLowerCase(Locale.ROOT).contains(query))
                    || (producto.pluBalanza() != null && String.valueOf(producto.pluBalanza()).contains(query)));
        });
    }

    private void procesarEntradaRapida() {
        String code = buscar.getText() == null ? "" : buscar.getText().trim();
        if (code.isBlank()) return;
        ejecutar(() -> {
            var regular = productoService.buscarPorCodigo(code);
            if (regular.isPresent()) {
                Producto producto = regular.get();
                if (producto.unidadMedida() == UnidadMedida.UN) agregarCantidad(producto, 1d, true);
                else pedirCantidad(producto, encontrarEnCarrito(producto));
                limpiarEscaneo();
                return;
            }

            if (code.matches("\\d+")) {
                try {
                    long productoId = Long.parseLong(code);
                    var porId = productoService.buscarPorId(productoId);
                    if (porId.isPresent()) {
                        Producto producto = porId.get();
                        if (producto.unidadMedida() == UnidadMedida.UN) agregarCantidad(producto, 1d, true);
                        else pedirCantidad(producto, encontrarEnCarrito(producto));
                        limpiarEscaneo();
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // Si no entra en long, puede seguir siendo un código/etiqueta de balanza.
                }
            }

            var lectura = codigoBarrasService.decodificarCodigoPeso(code, prefijoPeso);
            if (lectura.isPresent()) {
                var producto = productoService.buscarPorPlu(lectura.get().plu())
                        .orElseThrow(() -> new ValidationException("No existe un producto activo con PLU " + lectura.get().plu() + "."));
                if (producto.unidadMedida() != UnidadMedida.KG) {
                    throw new ValidationException("El PLU escaneado no corresponde a un producto vendido por kg.");
                }
                agregarCantidad(producto, lectura.get().pesoKg(), true);
                showFeedback(producto.nombre() + " · " + formatQuantityValue(lectura.get().pesoKg()) + " kg agregado");
                limpiarEscaneo();
                return;
            }
            throw new ValidationException("Producto no reconocido. Revisá el ID, código de barras o la configuración de la balanza.");
        });
    }

    private void limpiarEscaneo() {
        buscar.clear();
        buscar.requestFocus();
    }

    private void configurarProductos() {
        tablaProductos.setItems(filtrados);
        tablaProductos.setPlaceholder(new Label("No hay productos disponibles."));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaProductos.getStyleClass().add("pos-table");

        TableColumn<Producto, Producto> productoCol = new TableColumn<>("Producto");
        productoCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        productoCol.setPrefWidth(270);
        productoCol.setCellFactory(column -> new TableCell<>() {
            private final Label name = new Label();
            private final Label detail = new Label();
            private final VBox box = new VBox(1, name, detail);
            {
                name.getStyleClass().add("product-name");
                detail.getStyleClass().add("product-category");
            }
            @Override protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                name.setText(item.nombre());
                String category = item.categoriaNombre() == null ? "Sin categoría" : item.categoriaNombre();
                detail.setText(item.identificacionSistema() + " · " + category + " · " + item.identificacionComercial());
                setGraphic(box);
            }
        });

        TableColumn<Producto, String> priceCol = new TableColumn<>("Precio");
        priceCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().precioVenta())));
        priceCol.setPrefWidth(105);
        TableColumn<Producto, String> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatStock(cell.getValue())));
        stockCol.setPrefWidth(90);

        TableColumn<Producto, Producto> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(95);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button add = new Button("Agregar");
            { add.getStyleClass().add("pos-add-button"); }
            @Override protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                add.setDisable(item.stockActual() <= 0);
                add.setOnAction(event -> pedirCantidad(item, encontrarEnCarrito(item)));
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
        tablaCarrito.getStyleClass().addAll("pos-table", "pos-cart-table");

        TableColumn<CartItem, String> productCol = new TableColumn<>("Producto");
        productCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto.nombre()));
        productCol.setPrefWidth(170);
        TableColumn<CartItem, String> qtyCol = new TableColumn<>("Cant.");
        qtyCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQuantity(cell.getValue())));
        qtyCol.setPrefWidth(70);
        TableColumn<CartItem, String> subtotalCol = new TableColumn<>("Subtotal");
        subtotalCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().subtotal())));
        subtotalCol.setPrefWidth(105);
        TableColumn<CartItem, CartItem> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(105);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button edit = smallCartButton("Cant.");
            private final Button remove = smallCartButton("Quitar");
            private final HBox box = new HBox(4, edit, remove);
            {
                remove.getStyleClass().add("pos-remove-button");
                box.setAlignment(Pos.CENTER);
            }
            @Override protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                edit.setOnAction(event -> pedirCantidad(item.producto, item));
                remove.setOnAction(event -> { carrito.remove(item); recalcular(); buscar.requestFocus(); });
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
        MetodoPago selected = metodoPago.getValue();
        boolean cash = selected == MetodoPago.EFECTIVO;
        boolean credit = selected == MetodoPago.FIADO;
        boolean mixed = selected == MetodoPago.MIXTO;
        recibido.setDisable(!cash);

        if (mixed) {
            creditHint.setText(ventaService.puedeFiado(usuario)
                    ? "Podés combinar efectivo, transferencia y tarjeta. Si falta una parte, quedará fiada al cliente."
                    : "Podés combinar efectivo, transferencia y tarjeta. El total debe quedar completamente distribuido.");
        } else {
            creditHint.setText("Seleccionaremos el cliente al registrar la venta. El importe quedará pendiente en su cuenta.");
        }
        creditHint.setVisible(credit || mixed);
        creditHint.setManaged(credit || mixed);

        if (!cash) {
            recibido.clear();
            recibido.setPromptText(credit ? "Pendiente del cliente" : mixed ? "Se distribuye al cobrar" : "No aplica");
        } else {
            recibido.setPromptText("Efectivo recibido");
        }

        Node pay = lookup("#pos-pay-action");
        if (pay instanceof Button button) {
            button.setText(credit
                    ? "Registrar venta a crédito"
                    : mixed ? "Distribuir y cobrar" : "Cobrar venta");
        }
        recalcular();
    }

    private CartItem encontrarEnCarrito(Producto producto) {
        return carrito.stream().filter(item -> item.producto.id() == producto.id()).findFirst().orElse(null);
    }

    private void pedirCantidad(Producto producto, CartItem existing) {
        String initial = existing == null
                ? (producto.unidadMedida() == UnidadMedida.UN ? "1" : "0,5")
                : BigDecimal.valueOf(existing.cantidad).stripTrailingZeros().toPlainString().replace('.', ',');
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(existing == null ? "Agregar producto" : "Cambiar cantidad");
        dialog.setHeaderText(producto.nombre() + " · Stock: " + formatStock(producto));
        dialog.setContentText(producto.unidadMedida() == UnidadMedida.UN ? "Cantidad de unidades:" : "Cantidad en kg:");
        applyDialogStyle(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(value -> ejecutar(() -> {
            double qty = parseQuantity(value);
            validarCantidad(producto, qty);
            if (existing == null) carrito.add(new CartItem(producto, qty));
            else {
                existing.cantidad = qty;
                tablaCarrito.refresh();
            }
            recalcular();
            buscar.requestFocus();
        }));
    }

    private void agregarCantidad(Producto producto, double cantidad, boolean acumular) {
        validarCantidad(producto, cantidad);
        CartItem existing = encontrarEnCarrito(producto);
        double totalCantidad = existing == null ? cantidad : (acumular ? existing.cantidad + cantidad : cantidad);
        validarCantidad(producto, totalCantidad);
        if (existing == null) carrito.add(new CartItem(producto, cantidad));
        else {
            existing.cantidad = totalCantidad;
            tablaCarrito.refresh();
        }
        recalcular();
    }

    private void validarCantidad(Producto producto, double qty) {
        if (!Double.isFinite(qty) || qty <= 0) throw new ValidationException("La cantidad debe ser mayor a cero.");
        if (producto.unidadMedida() == UnidadMedida.UN && Math.abs(qty - Math.rint(qty)) > 0.000001) {
            throw new ValidationException("Este producto se vende por unidad y no acepta decimales.");
        }
        if (qty > producto.stockActual() + 0.000001) {
            throw new ValidationException("Stock insuficiente. Disponible: " + formatStock(producto));
        }
    }

    private void cobrar() {
        MetodoPago selectedMethod = metodoPago.getValue();
        if (carrito.isEmpty()) {
            showFeedback("Agregá al menos un producto a la venta.");
            return;
        }

        double totalVenta = carrito.stream().mapToDouble(CartItem::subtotal).sum();
        Cliente cliente = null;
        PagoMixtoDialog.Distribucion distribucion = null;

        if (selectedMethod == MetodoPago.FIADO) {
            Optional<Cliente> selected = ClientesFiadoDialog.seleccionarCliente(
                    getScene() == null ? null : getScene().getWindow(), ventaService, usuario, totalVenta
            );
            if (selected.isEmpty()) {
                buscar.requestFocus();
                return;
            }
            cliente = selected.get();
        } else if (selectedMethod == MetodoPago.MIXTO) {
            Optional<PagoMixtoDialog.Distribucion> selected = PagoMixtoDialog.show(
                    getScene() == null ? null : getScene().getWindow(),
                    totalVenta,
                    ventaService.puedeFiado(usuario)
            );
            if (selected.isEmpty()) {
                buscar.requestFocus();
                return;
            }
            distribucion = selected.get();

            if (distribucion.saldoFiado() > 0.000001d) {
                Optional<Cliente> selectedClient = ClientesFiadoDialog.seleccionarCliente(
                        getScene() == null ? null : getScene().getWindow(),
                        ventaService,
                        usuario,
                        distribucion.saldoFiado()
                );
                if (selectedClient.isEmpty()) {
                    buscar.requestFocus();
                    return;
                }
                cliente = selectedClient.get();
            }
        }

        Cliente clienteSeleccionado = cliente;
        PagoMixtoDialog.Distribucion distribucionSeleccionada = distribucion;
        ejecutar(() -> {
            List<LineaVenta> lineas = carrito.stream()
                    .map(item -> new LineaVenta(item.producto, item.cantidad))
                    .toList();

            VentaResultado result;
            if (selectedMethod == MetodoPago.MIXTO) {
                result = ventaService.venderMixto(
                        usuario,
                        caja,
                        lineas,
                        distribucionSeleccionada.pagos(),
                        clienteSeleccionado
                );
            } else {
                double recibidoValue = selectedMethod == MetodoPago.EFECTIVO
                        ? parseMoneyOrZero(recibido.getText())
                        : 0;
                result = ventaService.vender(
                        usuario,
                        caja,
                        lineas,
                        selectedMethod,
                        recibidoValue,
                        clienteSeleccionado
                );
            }

            carrito.clear();
            recibido.clear();
            recargarProductos();
            try {
                onVentaRegistrada.accept(result);
            } catch (RuntimeException ignored) {
                // La venta ya fue confirmada. El resumen se refrescará al volver a entrar a Caja.
            }

            if (selectedMethod == MetodoPago.FIADO) {
                showFeedback("Fiado registrado · " + clienteSeleccionado.nombre()
                        + " · Ticket #" + result.nroTicket() + " · " + formatCurrency(result.total()));
            } else if (selectedMethod == MetodoPago.MIXTO) {
                double fiado = result.monto(MetodoPago.FIADO);
                String extra = fiado > 0.000001d && clienteSeleccionado != null
                        ? " · Saldo de " + clienteSeleccionado.nombre() + ": " + formatCurrency(fiado)
                        : "";
                showFeedback("Venta mixta registrada · Ticket #" + result.nroTicket()
                        + " · " + formatCurrency(result.total()) + extra);
            } else {
                showFeedback("Venta registrada · Ticket #" + result.nroTicket() + " · " + formatCurrency(result.total()));
            }
            limpiarEscaneo();
        });
    }

    private void recargarProductos() {
        productos.setAll(productoService.listarActivos());
        tablaProductos.refresh();
    }

    private void recalcular() {
        double totalValue = carrito.stream().mapToDouble(CartItem::subtotal).sum();
        total.setText(formatCurrency(totalValue));
        double recibidoValue = metodoPago.getValue() == MetodoPago.EFECTIVO ? parseMoneySilently(recibido.getText()) : 0;
        vuelto.setText(formatCurrency(Math.max(0, recibidoValue - totalValue)));
    }

    private double parseQuantity(String value) {
        if (value == null || value.isBlank()) throw new ValidationException("Ingresá una cantidad.");
        try {
            return Double.parseDouble(value.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ValidationException("Ingresá una cantidad válida.");
        }
    }

    private double parseMoneyOrZero(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Double.parseDouble(normalizeMoney(value));
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el efectivo recibido.");
        }
    }

    private double parseMoneySilently(String value) {
        try { return parseMoneyOrZero(value); }
        catch (RuntimeException e) { return 0; }
    }

    private String normalizeMoney(String value) {
        String normalized = value.trim().replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        return normalized;
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private String formatStock(Producto producto) {
        String qty = formatQuantityValue(producto.stockActual());
        return qty + (producto.unidadMedida() == UnidadMedida.KG ? " kg" : " un.");
    }

    private String formatQuantity(CartItem item) {
        return formatQuantityValue(item.cantidad) + (item.producto.unidadMedida() == UnidadMedida.KG ? " kg" : "");
    }

    private String formatQuantityValue(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',');
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
            while (current.getCause() != null) current = current.getCause();
            showFeedback(current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage());
        }
    }

    private void showFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void applyDialogStyle(DialogPane pane) {
        addDialogStyle(pane, "/styles/app.css");
        addDialogStyle(pane, "/styles/venta.css");
    }

    private void addDialogStyle(DialogPane pane, String path) {
        var css = VentaView.class.getResource(path);
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }

    private static final class CartItem {
        private final Producto producto;
        private double cantidad;
        private CartItem(Producto producto, double cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
        }
        private double subtotal() { return producto.precioVenta() * cantidad; }
    }
}
