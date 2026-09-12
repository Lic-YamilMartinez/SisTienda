package py.sistienda.ui.inventario;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.InventarioConteoDetalle;
import py.sistienda.core.model.InventarioConteoItem;
import py.sistienda.core.model.InventarioConteoResumen;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;
import py.sistienda.core.service.InventarioService;
import py.sistienda.core.service.ProductoService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InventarioView extends BorderPane {
    private static final double EPSILON = 0.000001d;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final Usuario usuario;

    private final ObservableList<InventoryRow> rows = FXCollections.observableArrayList();
    private final FilteredList<InventoryRow> filtered = new FilteredList<>(rows, row -> true);
    private final TableView<InventoryRow> table = new TableView<>();
    private final TextField buscar = new TextField();
    private final Label totalProductos = metricValue();
    private final Label contados = metricValue();
    private final Label diferencias = metricValue();
    private final Label pendientes = metricValue();
    private final Label feedback = new Label();

    public InventarioView(ProductoService productoService, InventarioService inventarioService,
                          Usuario usuario, AutorizacionService autorizacionService) {
        this.productoService = productoService;
        this.inventarioService = inventarioService;
        this.usuario = usuario;
        autorizacionService.exigir(usuario, Permiso.INVENTARIO_GESTIONAR);

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 20, 24));
        setTop(buildHeader());
        setCenter(buildContent());
        configurarTabla();
        configurarBusqueda();
        recargarProductos();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("CONTROL DE MERCADERÍA");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Inventario físico");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Contá lo que realmente hay. SisTienda compara y ajusta sólo los productos que confirmes.");
        subtitle.getStyleClass().add("page-subtitle");

        Button historial = new Button("Historial de conteos");
        historial.getStyleClass().add("secondary-button");
        historial.setOnAction(event -> mostrarHistorial());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, new VBox(2, title, subtitle), spacer, historial);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);
        return new VBox(3, eyebrow, titleRow, feedback);
    }

    private VBox buildContent() {
        HBox metrics = new HBox(10,
                metricCard("PRODUCTOS", totalProductos, "Activos en catálogo"),
                metricCard("CONTADOS", contados, "Con cantidad física cargada"),
                metricCard("CON DIFERENCIA", diferencias, "Van a generar ajuste de stock"),
                metricCard("PENDIENTES", pendientes, "Todavía sin contar")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        buscar.setPromptText("Buscar o escanear producto...");
        buscar.getStyleClass().add("pos-search");
        HBox.setHgrow(buscar, Priority.ALWAYS);

        Button limpiar = new Button("Limpiar conteo");
        limpiar.getStyleClass().add("secondary-button");
        limpiar.setOnAction(event -> limpiarConteo());
        Button confirmar = new Button("Confirmar inventario");
        confirmar.getStyleClass().add("primary-button");
        confirmar.setOnAction(event -> confirmarInventario());
        HBox toolbar = new HBox(8, buscar, limpiar, confirmar);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label tip = new Label("Dejá vacío lo que todavía no contaste. Podés hacer inventarios parciales por sector o categoría sin tocar el resto del stock.");
        tip.getStyleClass().add("inventory-tip");
        tip.setWrapText(true);

        VBox card = new VBox(9, toolbar, tip, table);
        card.getStyleClass().add("inventory-card");
        card.setPadding(new Insets(14));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox body = new VBox(12, metrics, card);
        body.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(card, Priority.ALWAYS);
        return body;
    }

    private void configurarBusqueda() {
        buscar.textProperty().addListener((obs, oldValue, newValue) -> {
            String q = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(row -> q.isBlank()
                    || row.producto().nombre().toLowerCase(Locale.ROOT).contains(q)
                    || (row.producto().categoriaNombre() != null
                    && row.producto().categoriaNombre().toLowerCase(Locale.ROOT).contains(q))
                    || (row.producto().codigoBarras() != null
                    && row.producto().codigoBarras().toLowerCase(Locale.ROOT).contains(q))
                    || (row.producto().pluBalanza() != null
                    && row.producto().pluBalanza().toString().contains(q)));
        });
    }

    private void configurarTabla() {
        table.setItems(filtered);
        table.setPlaceholder(new Label("No hay productos para contar."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("inventory-table");

        TableColumn<InventoryRow, InventoryRow> productoCol = new TableColumn<>("Producto");
        productoCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        productoCol.setPrefWidth(285);
        productoCol.setCellFactory(column -> new TableCell<>() {
            private final Label name = new Label();
            private final Label detail = new Label();
            private final VBox box = new VBox(1, name, detail);
            {
                name.getStyleClass().add("product-name");
                detail.getStyleClass().add("product-category");
            }
            @Override protected void updateItem(InventoryRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                name.setText(item.producto().nombre());
                String category = item.producto().categoriaNombre() == null ? "Sin categoría" : item.producto().categoriaNombre();
                detail.setText(category + " · " + item.producto().identificacionComercial());
                setGraphic(box);
            }
        });

        TableColumn<InventoryRow, String> unidadCol = new TableColumn<>("Unidad");
        unidadCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().producto().unidadMedida() == UnidadMedida.UN ? "Unidades" : "Kilogramos"
        ));
        unidadCol.setPrefWidth(95);

        TableColumn<InventoryRow, String> sistemaCol = new TableColumn<>("Sistema");
        sistemaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQty(
                cell.getValue().producto().stockActual(), cell.getValue().producto().unidadMedida()
        )));
        sistemaCol.setPrefWidth(110);

        TableColumn<InventoryRow, InventoryRow> fisicoCol = new TableColumn<>("Conteo físico");
        fisicoCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        fisicoCol.setPrefWidth(145);
        fisicoCol.setCellFactory(column -> new TableCell<>() {
            private final TextField input = new TextField();
            private InventoryRow current;
            {
                input.setPromptText("Cantidad");
                input.getStyleClass().add("inventory-count-input");
                input.textProperty().addListener((obs, oldValue, newValue) -> {
                    if (current != null && !current.conteoProperty().get().equals(newValue)) {
                        current.conteoProperty().set(newValue);
                    }
                });
            }
            @Override protected void updateItem(InventoryRow item, boolean empty) {
                super.updateItem(item, empty);
                current = empty ? null : item;
                if (empty || item == null) { setGraphic(null); return; }
                if (!input.getText().equals(item.conteoProperty().get())) input.setText(item.conteoProperty().get());
                setGraphic(input);
            }
        });

        TableColumn<InventoryRow, String> diffCol = new TableColumn<>("Diferencia");
        diffCol.setCellValueFactory(cell -> cell.getValue().diferenciaProperty());
        diffCol.setPrefWidth(125);
        diffCol.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) { setGraphic(null); return; }
                badge.setText(text);
                badge.getStyleClass().removeAll("inventory-diff-plus", "inventory-diff-minus", "inventory-diff-ok", "inventory-diff-pending");
                InventoryRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || !row.contado()) badge.getStyleClass().add("inventory-diff-pending");
                else {
                    Double diff = row.diferenciaNumerica();
                    if (diff == null || Math.abs(diff) <= EPSILON) badge.getStyleClass().add("inventory-diff-ok");
                    else if (diff > 0) badge.getStyleClass().add("inventory-diff-plus");
                    else badge.getStyleClass().add("inventory-diff-minus");
                }
                setAlignment(Pos.CENTER);
                setGraphic(badge);
            }
        });

        table.getColumns().setAll(productoCol, unidadCol, sistemaCol, fisicoCol, diffCol);
    }

    private void recargarProductos() {
        ejecutar(() -> {
            rows.clear();
            for (Producto producto : productoService.listarActivos()) {
                InventoryRow row = new InventoryRow(producto);
                row.conteoProperty().addListener((obs, oldValue, newValue) -> actualizarMetricas());
                rows.add(row);
            }
            actualizarMetricas();
        });
    }

    private void actualizarMetricas() {
        long counted = rows.stream().filter(InventoryRow::contado).count();
        long diff = rows.stream().filter(row -> {
            Double value = row.diferenciaNumerica();
            return value != null && Math.abs(value) > EPSILON;
        }).count();
        totalProductos.setText(Integer.toString(rows.size()));
        contados.setText(Long.toString(counted));
        diferencias.setText(Long.toString(diff));
        pendientes.setText(Long.toString(Math.max(0, rows.size() - counted)));
    }

    private void limpiarConteo() {
        for (InventoryRow row : rows) row.conteoProperty().set("");
        mostrarFeedback("Conteo limpiado. No se modificó ningún stock.");
    }

    private void confirmarInventario() {
        ejecutar(() -> {
            List<InventarioConteoItem> items = new ArrayList<>();
            for (InventoryRow row : rows) {
                if (row.contado()) items.add(row.toItem());
            }
            if (items.isEmpty()) throw new ValidationException("Cargá al menos un conteo físico antes de confirmar.");

            long changed = items.stream().filter(item -> Math.abs(item.diferencia()) > EPSILON).count();
            Dialog<ButtonType> dialog = new Dialog<>();
            if (getScene() != null) dialog.initOwner(getScene().getWindow());
            dialog.setTitle("Confirmar inventario físico");
            dialog.setHeaderText(items.size() + " productos contados · " + changed + " con diferencia");
            ButtonType confirmar = new ButtonType("Aplicar ajustes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(520);

            TextField motivo = new TextField("Conteo físico");
            motivo.setPromptText("Ej.: Conteo semanal");
            TextArea observacion = new TextArea();
            observacion.setPromptText("Observación opcional");
            observacion.setPrefRowCount(3);
            observacion.setWrapText(true);
            Label warning = new Label(changed == 0
                    ? "No hay diferencias. El conteo quedará guardado como control sin modificar stock."
                    : "SisTienda ajustará únicamente los productos con diferencia y dejará auditoría del usuario, fecha y motivo.");
            warning.getStyleClass().add("inventory-confirm-hint");
            warning.setWrapText(true);
            Label error = new Label();
            error.getStyleClass().add("form-error");
            error.setWrapText(true);

            VBox content = new VBox(9,
                    warning,
                    field("Motivo *", motivo),
                    field("Observación", observacion),
                    error
            );
            dialog.getDialogPane().setContent(content);
            applyStyles(dialog);

            final long[] inventoryId = {0};
            Node save = dialog.getDialogPane().lookupButton(confirmar);
            save.addEventFilter(ActionEvent.ACTION, event -> {
                try {
                    var result = inventarioService.registrar(usuario, motivo.getText(), observacion.getText(), items);
                    inventoryId[0] = result.id();
                } catch (RuntimeException e) {
                    error.setText(rootMessage(e));
                    event.consume();
                }
            });

            if (dialog.showAndWait().filter(confirmar::equals).isPresent() && inventoryId[0] > 0) {
                mostrarFeedback("Inventario #" + inventoryId[0] + " registrado correctamente.");
                recargarProductos();
            }
        });
    }

    private void mostrarHistorial() {
        ejecutar(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            if (getScene() != null) dialog.initOwner(getScene().getWindow());
            dialog.setTitle("Historial de inventarios");
            dialog.setHeaderText("Conteos físicos registrados");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefSize(900, 620);

            TableView<InventarioConteoResumen> history = new TableView<>(FXCollections.observableArrayList(
                    inventarioService.recientes(usuario, 100)
            ));
            history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            history.setPlaceholder(new Label("Todavía no hay inventarios registrados."));

            TableColumn<InventarioConteoResumen, String> fecha = new TableColumn<>("Fecha");
            fecha.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().fecha())));
            fecha.setPrefWidth(150);
            TableColumn<InventarioConteoResumen, String> motivo = new TableColumn<>("Motivo");
            motivo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().motivo()));
            motivo.setPrefWidth(250);
            TableColumn<InventarioConteoResumen, String> user = new TableColumn<>("Usuario");
            user.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
            user.setPrefWidth(120);
            TableColumn<InventarioConteoResumen, String> counted = new TableColumn<>("Contados");
            counted.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Integer.toString(cell.getValue().productosContados())));
            counted.setPrefWidth(90);
            TableColumn<InventarioConteoResumen, String> adjusted = new TableColumn<>("Ajustados");
            adjusted.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Integer.toString(cell.getValue().productosAjustados())));
            adjusted.setPrefWidth(90);
            history.getColumns().setAll(fecha, motivo, user, counted, adjusted);
            history.setRowFactory(view -> {
                TableRow<InventarioConteoResumen> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty()) mostrarDetalle(row.getItem());
                });
                return row;
            });

            Label hint = new Label("Doble clic sobre un conteo para ver sistema vs. físico y la diferencia registrada.");
            hint.getStyleClass().add("inventory-tip");
            VBox content = new VBox(8, hint, history);
            VBox.setVgrow(history, Priority.ALWAYS);
            dialog.getDialogPane().setContent(content);
            applyStyles(dialog);
            dialog.showAndWait();
        });
    }

    private void mostrarDetalle(InventarioConteoResumen resumen) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Inventario #" + resumen.id());
        dialog.setHeaderText(resumen.motivo() + " · " + DATE_TIME.format(resumen.fecha()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(760, 560);

        TableView<InventarioConteoDetalle> detail = new TableView<>(FXCollections.observableArrayList(
                inventarioService.detalle(usuario, resumen.id())
        ));
        detail.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<InventarioConteoDetalle, String> product = new TableColumn<>("Producto");
        product.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto()));
        product.setPrefWidth(260);
        TableColumn<InventarioConteoDetalle, String> system = new TableColumn<>("Sistema");
        system.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQty(cell.getValue().stockSistema(), cell.getValue().unidadMedida())));
        system.setPrefWidth(120);
        TableColumn<InventarioConteoDetalle, String> physical = new TableColumn<>("Físico");
        physical.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQty(cell.getValue().stockFisico(), cell.getValue().unidadMedida())));
        physical.setPrefWidth(120);
        TableColumn<InventarioConteoDetalle, String> diff = new TableColumn<>("Diferencia");
        diff.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatSigned(cell.getValue().diferencia(), cell.getValue().unidadMedida())));
        diff.setPrefWidth(120);
        detail.getColumns().setAll(product, system, physical, diff);

        Label meta = new Label("Usuario: " + resumen.usuario()
                + (resumen.observacion() == null ? "" : " · " + resumen.observacion()));
        meta.getStyleClass().add("inventory-tip");
        meta.setWrapText(true);
        VBox content = new VBox(8, meta, detail);
        VBox.setVgrow(detail, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);
        dialog.showAndWait();
    }

    private VBox field(String text, javafx.scene.control.Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
    }

    private static VBox metricCard(String title, Label value, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("inventory-metric-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("inventory-metric-hint");
        hintLabel.setWrapText(true);
        VBox card = new VBox(3, titleLabel, value, hintLabel);
        card.getStyleClass().add("inventory-metric-card");
        card.setPadding(new Insets(12));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label metricValue() {
        Label value = new Label("0");
        value.getStyleClass().add("inventory-metric-value");
        return value;
    }

    private String formatQty(double value, UnidadMedida unidad) {
        if (unidad == UnidadMedida.UN) {
            return NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
        }
        return String.format(new Locale("es", "PY"), "%.3f kg", value);
    }

    private String formatSigned(double value, UnidadMedida unidad) {
        String sign = value > EPSILON ? "+" : "";
        return sign + formatQty(value, unidad);
    }

    private double parseQty(String text, UnidadMedida unidad) {
        if (text == null || text.isBlank()) throw new ValidationException("Ingresá el conteo físico.");
        String normalized = text.trim().replace("kg", "").replace("KG", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (unidad == UnidadMedida.UN && normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double value = Double.parseDouble(normalized);
            if (!Double.isFinite(value) || value < 0) throw new NumberFormatException();
            if (unidad == UnidadMedida.UN && Math.abs(value - Math.rint(value)) > EPSILON) {
                throw new ValidationException("Los productos por unidad deben contarse con números enteros.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new ValidationException("Ingresá una cantidad física válida.");
        }
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false);
            feedback.setManaged(false);
            action.run();
        } catch (RuntimeException e) {
            feedback.setText(rootMessage(e));
            feedback.setVisible(true);
            feedback.setManaged(true);
        }
    }

    private void mostrarFeedback(String text) {
        feedback.setText(text);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private void applyStyles(Dialog<?> dialog) {
        var app = InventarioView.class.getResource("/styles/app.css");
        if (app != null) dialog.getDialogPane().getStylesheets().add(app.toExternalForm());
        var css = InventarioView.class.getResource("/styles/inventario.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }

    private final class InventoryRow {
        private final Producto producto;
        private final StringProperty conteo = new SimpleStringProperty("");
        private final StringProperty diferencia = new SimpleStringProperty("Pendiente");

        private InventoryRow(Producto producto) {
            this.producto = producto;
            conteo.addListener((obs, oldValue, newValue) -> recalcularDiferencia());
        }

        private Producto producto() {
            return producto;
        }

        private StringProperty conteoProperty() {
            return conteo;
        }

        private StringProperty diferenciaProperty() {
            return diferencia;
        }

        private boolean contado() {
            return conteo.get() != null && !conteo.get().isBlank();
        }

        private Double diferenciaNumerica() {
            if (!contado()) return null;
            try {
                return parseQty(conteo.get(), producto.unidadMedida()) - producto.stockActual();
            } catch (RuntimeException e) {
                return null;
            }
        }

        private void recalcularDiferencia() {
            if (!contado()) {
                diferencia.set("Pendiente");
                return;
            }
            try {
                double diff = parseQty(conteo.get(), producto.unidadMedida()) - producto.stockActual();
                diferencia.set(Math.abs(diff) <= EPSILON ? "Sin diferencia" : formatSigned(diff, producto.unidadMedida()));
            } catch (RuntimeException e) {
                diferencia.set("Revisar");
            }
        }

        private InventarioConteoItem toItem() {
            double fisico = parseQty(conteo.get(), producto.unidadMedida());
            return new InventarioConteoItem(
                    producto.id(), producto.nombre(), producto.unidadMedida(), producto.stockActual(), fisico
            );
        }
    }
}
