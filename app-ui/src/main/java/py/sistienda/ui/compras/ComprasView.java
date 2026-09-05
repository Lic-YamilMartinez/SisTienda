package py.sistienda.ui.compras;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.*;
import py.sistienda.core.service.CompraService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.ProveedorService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class ComprasView extends BorderPane {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProveedorService proveedorService;
    private final ProductoService productoService;
    private final CompraService compraService;
    private final Usuario usuario;

    private final ObservableList<CompraResumen> compras = FXCollections.observableArrayList();
    private final FilteredList<CompraResumen> filtradas = new FilteredList<>(compras, value -> true);
    private final TableView<CompraResumen> tabla = new TableView<>();
    private final TextField buscar = new TextField();
    private final Label proveedoresValue = metricValue();
    private final Label comprasValue = metricValue();
    private final Label invertidoValue = metricValue();
    private final Label feedback = new Label();

    public ComprasView(ProveedorService proveedorService, ProductoService productoService, CompraService compraService, Usuario usuario) {
        this.proveedorService = proveedorService;
        this.productoService = productoService;
        this.compraService = compraService;
        this.usuario = usuario;

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 20, 24));
        setTop(buildHeader());
        setCenter(buildContent());
        configurarTabla();
        configurarFiltro();
        recargar();
    }

    private HBox buildHeader() {
        Label eyebrow = new Label("ABASTECIMIENTO");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Compras");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Registrá compras y actualizá stock y costos en una sola operación.");
        subtitle.getStyleClass().add("page-subtitle");
        VBox heading = new VBox(2, eyebrow, title, subtitle);

        Button proveedores = new Button("Proveedores");
        proveedores.getStyleClass().add("secondary-button");
        proveedores.setOnAction(event -> mostrarProveedores());

        Button nueva = new Button("+ Nueva compra");
        nueva.getStyleClass().add("primary-button");
        nueva.setOnAction(event -> nuevaCompra());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, heading, spacer, proveedores, nueva);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        return header;
    }

    private VBox buildContent() {
        HBox metrics = new HBox(10,
                metricCard("PROVEEDORES", proveedoresValue, "Activos"),
                metricCard("COMPRAS", comprasValue, "Últimas registradas"),
                metricCard("INVERSIÓN", invertidoValue, "En compras cargadas")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        buscar.setPromptText("Buscar proveedor o documento...");
        buscar.getStyleClass().add("purchase-search");
        buscar.setPrefWidth(350);

        feedback.getStyleClass().add("purchase-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);

        HBox toolbar = new HBox(10, buscar);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 12, 10, 12));

        VBox history = new VBox(0, toolbar, feedback, tabla);
        history.getStyleClass().add("purchase-history-card");
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox root = new VBox(10, metrics, history);
        VBox.setVgrow(history, Priority.ALWAYS);
        return root;
    }

    private VBox metricCard(String title, Label value, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("purchase-metric-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("purchase-metric-hint");
        VBox card = new VBox(3, titleLabel, value, hintLabel);
        card.getStyleClass().add("purchase-metric-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Label metricValue() {
        Label label = new Label("0");
        label.getStyleClass().add("purchase-metric-value");
        return label;
    }

    private void configurarFiltro() {
        buscar.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtradas.setPredicate(compra -> query.isBlank()
                    || compra.proveedor().toLowerCase(Locale.ROOT).contains(query)
                    || (compra.nroDocumento() != null && compra.nroDocumento().toLowerCase(Locale.ROOT).contains(query)));
        });
    }

    private void configurarTabla() {
        tabla.setItems(filtradas);
        tabla.setPlaceholder(new Label("Todavía no hay compras registradas."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getStyleClass().add("purchase-table");

        TableColumn<CompraResumen, String> fechaCol = new TableColumn<>("Fecha");
        fechaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().fecha())));
        fechaCol.setPrefWidth(135);

        TableColumn<CompraResumen, String> proveedorCol = new TableColumn<>("Proveedor");
        proveedorCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().proveedor()));
        proveedorCol.setPrefWidth(220);

        TableColumn<CompraResumen, String> docCol = new TableColumn<>("Documento");
        docCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(orDash(cell.getValue().nroDocumento())));
        docCol.setPrefWidth(170);

        TableColumn<CompraResumen, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().total())));
        totalCol.setPrefWidth(130);

        TableColumn<CompraResumen, String> userCol = new TableColumn<>("Usuario");
        userCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
        userCol.setPrefWidth(100);

        TableColumn<CompraResumen, CompraResumen> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button("Ver detalle");
            { button.getStyleClass().add("purchase-detail-button"); }
            @Override protected void updateItem(CompraResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                button.setOnAction(event -> mostrarDetalle(item.id()));
                setAlignment(Pos.CENTER);
                setGraphic(button);
            }
        });

        tabla.getColumns().setAll(fechaCol, proveedorCol, docCol, totalCol, userCol, actionCol);
        tabla.setRowFactory(view -> {
            TableRow<CompraResumen> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) mostrarDetalle(row.getItem().id());
            });
            return row;
        });
    }

    private void recargar() {
        ejecutar(() -> {
            List<Proveedor> proveedores = proveedorService.listarActivos();
            compras.setAll(compraService.listarRecientes());
            proveedoresValue.setText(Integer.toString(proveedores.size()));
            comprasValue.setText(Integer.toString(compras.size()));
            invertidoValue.setText(formatCurrency(compras.stream().mapToDouble(CompraResumen::total).sum()));
        });
    }

    private void nuevaCompra() {
        List<Proveedor> proveedores = proveedorService.listarActivos();
        if (proveedores.isEmpty()) {
            mostrarFeedback("Creá primero un proveedor para registrar una compra.");
            mostrarProveedores();
            return;
        }
        List<Producto> productos = productoService.listarActivos();
        if (productos.isEmpty()) {
            mostrarFeedback("No hay productos activos para comprar.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Nueva compra");
        dialog.setHeaderText("Registrar entrada de mercadería");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Registrar compra");
        dialog.getDialogPane().setPrefSize(860, 650);

        ComboBox<Proveedor> proveedor = new ComboBox<>(FXCollections.observableArrayList(proveedores));
        proveedor.setValue(proveedores.getFirst());
        proveedor.setConverter(proveedorConverter());
        proveedor.setMaxWidth(Double.MAX_VALUE);

        TextField documento = new TextField();
        documento.setPromptText("Factura / remisión / referencia");
        TextField observacion = new TextField();
        observacion.setPromptText("Observación opcional");

        ComboBox<Producto> producto = new ComboBox<>(FXCollections.observableArrayList(productos));
        producto.setConverter(productoConverter());
        producto.setPromptText("Elegí un producto");
        producto.setMaxWidth(Double.MAX_VALUE);
        TextField cantidad = new TextField();
        cantidad.setPromptText("Cantidad");
        cantidad.setPrefWidth(110);
        TextField costo = new TextField();
        costo.setPromptText("Costo unitario");
        costo.setPrefWidth(150);
        Button agregar = new Button("Agregar");
        agregar.getStyleClass().add("purchase-add-button");

        ObservableList<LineaCompra> lineas = FXCollections.observableArrayList();
        TableView<LineaCompra> lineTable = buildLineTable(lineas);
        Label total = new Label("TOTAL  Gs. 0");
        total.getStyleClass().add("purchase-total");

        agregar.setOnAction(event -> {
            try {
                Producto selected = producto.getValue();
                if (selected == null) throw new ValidationException("Elegí un producto.");
                double qty = parseNumber(cantidad.getText(), "cantidad");
                double unitCost = parseNumber(costo.getText(), "costo");
                if (qty <= 0) throw new ValidationException("La cantidad debe ser mayor a cero.");
                if (selected.unidadMedida() == UnidadMedida.UN && Math.abs(qty - Math.rint(qty)) > 0.000001) {
                    throw new ValidationException("Este producto se compra por unidad y no acepta decimales.");
                }
                if (unitCost < 0) throw new ValidationException("El costo no puede ser negativo.");
                lineas.removeIf(linea -> linea.producto().id() == selected.id());
                lineas.add(new LineaCompra(selected, qty, unitCost));
                total.setText("TOTAL  " + formatCurrency(lineas.stream().mapToDouble(LineaCompra::subtotal).sum()));
                producto.setValue(null); cantidad.clear(); costo.clear();
            } catch (ValidationException e) {
                showInlineError(dialog, e.getMessage());
            }
        });

        producto.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) costo.setText(formatPlain(newValue.costo()));
        });

        VBox providerField = field("Proveedor *", proveedor);
        VBox docField = field("Documento", documento);
        HBox top = new HBox(10, providerField, docField);
        HBox.setHgrow(providerField, Priority.ALWAYS);
        HBox.setHgrow(docField, Priority.ALWAYS);

        HBox addRow = new HBox(8, producto, cantidad, costo, agregar);
        HBox.setHgrow(producto, Priority.ALWAYS);
        addRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox totalRow = new HBox(10, spacer, total);

        VBox content = new VBox(10, top, field("Observación", observacion), new Separator(), addRow, lineTable, totalRow);
        VBox.setVgrow(lineTable, Priority.ALWAYS);
        content.setPadding(new Insets(6));
        dialog.getDialogPane().setContent(content);
        applyDialogStyles(dialog);

        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        long[] saved = {0};
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                saved[0] = compraService.registrar(usuario, proveedor.getValue(), documento.getText(), List.copyOf(lineas), observacion.getText());
            } catch (RuntimeException e) {
                showInlineError(dialog, rootMessage(e));
                event.consume();
            }
        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> {
            mostrarFeedback("Compra registrada correctamente · #" + saved[0]);
            recargar();
        });
    }

    private TableView<LineaCompra> buildLineTable(ObservableList<LineaCompra> lineas) {
        TableView<LineaCompra> table = new TableView<>(lineas);
        table.setPlaceholder(new Label("Agregá productos a la compra."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(330);

        TableColumn<LineaCompra, String> product = new TableColumn<>("Producto");
        product.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto().nombre()));
        product.setPrefWidth(260);
        TableColumn<LineaCompra, String> qty = new TableColumn<>("Cant.");
        qty.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQuantity(cell.getValue().cantidad())));
        qty.setPrefWidth(80);
        TableColumn<LineaCompra, String> cost = new TableColumn<>("Costo");
        cost.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().costoUnitario())));
        cost.setPrefWidth(120);
        TableColumn<LineaCompra, String> subtotal = new TableColumn<>("Subtotal");
        subtotal.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().subtotal())));
        subtotal.setPrefWidth(130);
        TableColumn<LineaCompra, LineaCompra> action = new TableColumn<>("");
        action.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        action.setPrefWidth(80);
        action.setCellFactory(col -> new TableCell<>() {
            private final Button remove = new Button("Quitar");
            { remove.getStyleClass().add("purchase-remove-button"); }
            @Override protected void updateItem(LineaCompra item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                remove.setOnAction(event -> lineas.remove(item));
                setAlignment(Pos.CENTER); setGraphic(remove);
            }
        });
        table.getColumns().setAll(product, qty, cost, subtotal, action);
        return table;
    }

    private void mostrarProveedores() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Proveedores");
        dialog.setHeaderText("Gestionar proveedores");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(760, 560);

        ObservableList<Proveedor> items = FXCollections.observableArrayList(proveedorService.listarActivos());
        FilteredList<Proveedor> filtered = new FilteredList<>(items, value -> true);
        TextField search = new TextField();
        search.setPromptText("Buscar proveedor...");
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            String q = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(p -> q.isBlank() || p.nombre().toLowerCase(Locale.ROOT).contains(q)
                    || (p.ruc() != null && p.ruc().toLowerCase(Locale.ROOT).contains(q)));
        });

        TableView<Proveedor> table = new TableView<>(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Todavía no hay proveedores."));
        TableColumn<Proveedor, String> name = new TableColumn<>("Proveedor");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().nombre()));
        name.setPrefWidth(220);
        TableColumn<Proveedor, String> rucCol = new TableColumn<>("RUC");
        rucCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(orDash(cell.getValue().ruc())));
        rucCol.setPrefWidth(130);
        TableColumn<Proveedor, String> phone = new TableColumn<>("Teléfono");
        phone.setCellValueFactory(cell -> new ReadOnlyStringWrapper(orDash(cell.getValue().telefono())));
        phone.setPrefWidth(140);
        table.getColumns().setAll(name, rucCol, phone);

        Button add = new Button("+ Nuevo"); add.getStyleClass().add("primary-button");
        Button edit = new Button("Editar"); edit.getStyleClass().add("secondary-button");
        Button deactivate = new Button("Desactivar"); deactivate.getStyleClass().add("purchase-danger-button");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deactivate.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        add.setOnAction(event -> editarProveedor(null, items));
        edit.setOnAction(event -> editarProveedor(table.getSelectionModel().getSelectedItem(), items));
        deactivate.setOnAction(event -> {
            Proveedor selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "¿Desactivar a " + selected.nombre() + "?", ButtonType.CANCEL, ButtonType.OK);
            applyDialogStyles(confirm);
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> {
                proveedorService.desactivar(selected);
                items.setAll(proveedorService.listarActivos());
            });
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, search, spacer, add, edit, deactivate);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, actions, table); VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        applyDialogStyles(dialog);
        dialog.showAndWait();
        recargar();
    }

    private void editarProveedor(Proveedor actual, ObservableList<Proveedor> target) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(actual == null ? "Nuevo proveedor" : "Editar proveedor");
        dialog.setHeaderText(actual == null ? "Agregar proveedor" : actual.nombre());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Guardar");

        TextField nombre = new TextField(actual == null ? "" : actual.nombre());
        TextField ruc = new TextField(actual == null ? "" : empty(actual.ruc()));
        TextField telefono = new TextField(actual == null ? "" : empty(actual.telefono()));
        TextField email = new TextField(actual == null ? "" : empty(actual.email()));
        TextField direccion = new TextField(actual == null ? "" : empty(actual.direccion()));
        VBox content = new VBox(8, field("Nombre *", nombre), field("RUC", ruc), field("Teléfono", telefono),
                field("Correo", email), field("Dirección", direccion));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(480);
        applyDialogStyles(dialog);

        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (actual == null) proveedorService.crear(nombre.getText(), ruc.getText(), telefono.getText(), email.getText(), direccion.getText());
                else proveedorService.actualizar(actual, nombre.getText(), ruc.getText(), telefono.getText(), email.getText(), direccion.getText());
            } catch (RuntimeException e) {
                showInlineError(dialog, rootMessage(e));
                event.consume();
            }
        });
        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> target.setAll(proveedorService.listarActivos()));
    }

    private void mostrarDetalle(long compraId) {
        ejecutar(() -> {
            CompraDetalle detalle = compraService.detalle(compraId);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Compra #" + detalle.id());
            dialog.setHeaderText(detalle.proveedor().nombre() + " · " + DATE_TIME.format(detalle.fecha()));
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefSize(700, 540);

            Label meta = new Label("Documento: " + orDash(detalle.nroDocumento()) + "   ·   Usuario: " + detalle.usuario());
            meta.getStyleClass().add("purchase-detail-meta");
            TableView<CompraDetalleItem> items = new TableView<>(FXCollections.observableArrayList(detalle.items()));
            items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            TableColumn<CompraDetalleItem, String> product = new TableColumn<>("Producto");
            product.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto()));
            TableColumn<CompraDetalleItem, String> qty = new TableColumn<>("Cant.");
            qty.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQuantity(cell.getValue().cantidad())));
            TableColumn<CompraDetalleItem, String> cost = new TableColumn<>("Costo");
            cost.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().costoUnitario())));
            TableColumn<CompraDetalleItem, String> subtotal = new TableColumn<>("Subtotal");
            subtotal.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().subtotal())));
            items.getColumns().setAll(product, qty, cost, subtotal);

            Label total = new Label("TOTAL  " + formatCurrency(detalle.total()));
            total.getStyleClass().add("purchase-total");
            Label obs = new Label(detalle.observacion() == null ? "" : detalle.observacion());
            obs.setWrapText(true); obs.getStyleClass().add("purchase-detail-meta");
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox footer = new HBox(10, obs, spacer, total); footer.setAlignment(Pos.CENTER_LEFT);
            VBox content = new VBox(10, meta, items, footer); VBox.setVgrow(items, Priority.ALWAYS);
            dialog.getDialogPane().setContent(content);
            applyDialogStyles(dialog);
            dialog.showAndWait();
        });
    }

    private VBox field(String labelText, Control control) {
        Label label = new Label(labelText); label.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, label, control); box.setMaxWidth(Double.MAX_VALUE); return box;
    }

    private StringConverter<Proveedor> proveedorConverter() {
        return new StringConverter<>() {
            @Override public String toString(Proveedor value) { return value == null ? "" : value.nombre(); }
            @Override public Proveedor fromString(String value) { return null; }
        };
    }

    private StringConverter<Producto> productoConverter() {
        return new StringConverter<>() {
            @Override public String toString(Producto value) { return value == null ? "" : value.nombre(); }
            @Override public Producto fromString(String value) { return null; }
        };
    }

    private double parseNumber(String value, String field) {
        if (value == null || value.isBlank()) throw new ValidationException("Ingresá la " + field + ".");
        String normalized = value.trim().replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try { return Double.parseDouble(normalized); }
        catch (NumberFormatException e) { throw new ValidationException("Revisá la " + field + ". Usá sólo números."); }
    }

    private String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
    }
    private String formatPlain(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private String formatQuantity(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private String orDash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private String empty(String value) { return value == null ? "" : value; }

    private void showInlineError(Dialog<?> dialog, String message) {
        dialog.setHeaderText(message == null ? "Revisá los datos ingresados." : message);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message); feedback.setVisible(true); feedback.setManaged(true);
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false); feedback.setManaged(false); action.run();
        } catch (RuntimeException e) {
            mostrarFeedback(rootMessage(e));
        }
    }

    private void applyDialogStyles(Dialog<?> dialog) {
        addStyle(dialog, "/styles/app.css"); addStyle(dialog, "/styles/compras.css");
    }
    private void addStyle(Dialog<?> dialog, String path) {
        var css = ComprasView.class.getResource(path);
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
