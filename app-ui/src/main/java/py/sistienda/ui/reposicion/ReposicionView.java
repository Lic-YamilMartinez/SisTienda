package py.sistienda.ui.reposicion;

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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.ReposicionItem;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.service.ReposicionService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class ReposicionView extends BorderPane {
    private static final double EPSILON = 0.000001d;

    private final ReposicionService service;
    private final ObservableList<ReposicionItem> items = FXCollections.observableArrayList();
    private final FilteredList<ReposicionItem> filtrados = new FilteredList<>(items, value -> true);
    private final TextField buscar = new TextField();
    private final TableView<ReposicionItem> tabla = new TableView<>();
    private final Label cantidad = metricValue();
    private final Label sinStock = metricValue();
    private final Label inversion = metricValue();
    private final Label configurados = metricValue();
    private final Label feedback = new Label();

    public ReposicionView(ReposicionService service) {
        this.service = service;
        getStyleClass().add("content-area");
        setPadding(new Insets(28, 32, 28, 32));
        setTop(buildHeader());
        setCenter(buildTable());
        configurarTabla();
        buscar.textProperty().addListener((obs, oldValue, newValue) -> aplicarFiltro());
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("REPOSICIÓN");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Qué comprar");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("SisTienda compara el stock actual con tus niveles mínimo e ideal y arma una lista de reposición.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);

        Button copiar = new Button("Copiar lista");
        copiar.getStyleClass().add("secondary-button");
        copiar.setOnAction(event -> copiarLista());
        Button actualizar = new Button("Actualizar");
        actualizar.getStyleClass().add("primary-button");
        actualizar.setOnAction(event -> recargar());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, new VBox(2, eyebrow, title, subtitle), spacer, copiar, actualizar);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        HBox cards = new HBox(12,
                metricCard("A reponer", cantidad, "Productos en nivel crítico"),
                metricCard("Sin stock", sinStock, "Prioridad inmediata"),
                metricCard("Inversión estimada", inversion, "Según costo actual"),
                metricCard("Configurados", configurados, "Con mínimo e ideal definidos")
        );
        cards.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox header = new VBox(16, titleRow, feedback, cards);
        header.setPadding(new Insets(0, 0, 18, 0));
        return header;
    }

    private VBox buildTable() {
        buscar.setPromptText("Buscar producto o categoría...");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(360);
        Label hint = new Label("Los productos sin stock aparecen primero. La sugerencia completa hasta el stock ideal.");
        hint.getStyleClass().add("result-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, buscar, spacer, hint);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("catalog-toolbar");

        tabla.setItems(filtrados);
        tabla.setPlaceholder(new Label("Todo al día. No hay productos que necesiten reposición con la configuración actual."));
        VBox panel = new VBox(0, toolbar, tabla);
        panel.getStyleClass().add("table-panel");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return panel;
    }

    private void configurarTabla() {
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ReposicionItem, String> producto = new TableColumn<>("Producto");
        producto.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto().nombre()));
        producto.setPrefWidth(240);

        TableColumn<ReposicionItem, String> categoria = new TableColumn<>("Categoría");
        categoria.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().producto().categoriaNombre() == null ? "Sin categoría" : cell.getValue().producto().categoriaNombre()));
        categoria.setPrefWidth(130);

        TableColumn<ReposicionItem, String> actual = new TableColumn<>("Actual");
        actual.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCantidad(cell.getValue().producto().stockActual(), cell.getValue().producto())));
        actual.setPrefWidth(90);

        TableColumn<ReposicionItem, String> minimo = new TableColumn<>("Mínimo");
        minimo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCantidad(cell.getValue().producto().stockMinimo(), cell.getValue().producto())));
        minimo.setPrefWidth(90);

        TableColumn<ReposicionItem, String> ideal = new TableColumn<>("Ideal");
        ideal.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCantidad(cell.getValue().producto().stockIdeal(), cell.getValue().producto())));
        ideal.setPrefWidth(90);

        TableColumn<ReposicionItem, String> sugerido = new TableColumn<>("Comprar");
        sugerido.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCantidad(cell.getValue().cantidadSugerida(), cell.getValue().producto())));
        sugerido.setPrefWidth(105);
        sugerido.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().remove("replenishment-qty");
                if (!empty) getStyleClass().add("replenishment-qty");
            }
        });

        TableColumn<ReposicionItem, String> costo = new TableColumn<>("Inversión");
        costo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().inversionEstimada())));
        costo.setPrefWidth(120);

        TableColumn<ReposicionItem, ReposicionItem> acciones = new TableColumn<>("");
        acciones.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        acciones.setPrefWidth(105);
        acciones.setCellFactory(column -> new TableCell<>() {
            private final Button button = new Button("Niveles");
            { button.getStyleClass().add("table-action-button"); }
            @Override protected void updateItem(ReposicionItem value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setGraphic(null); return; }
                button.setOnAction(event -> editarNiveles(value.producto()));
                setAlignment(Pos.CENTER);
                setGraphic(button);
            }
        });

        tabla.getColumns().setAll(producto, categoria, actual, minimo, ideal, sugerido, costo, acciones);
        tabla.setRowFactory(view -> new TableRow<>() {
            @Override protected void updateItem(ReposicionItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("replenishment-empty-row", "replenishment-low-row");
                if (!empty && item != null) {
                    getStyleClass().add(item.sinStock() ? "replenishment-empty-row" : "replenishment-low-row");
                }
            }
        });
    }

    private void editarNiveles(Producto producto) {
        Dialog<double[]> dialog = new Dialog<>();
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Niveles de reposición");
        dialog.setHeaderText(producto.nombre());
        ButtonType guardar = new ButtonType("Guardar niveles", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        TextField minimo = new TextField(formatInput(producto.stockMinimo()));
        TextField ideal = new TextField(formatInput(producto.stockIdeal()));
        minimo.getStyleClass().add("form-control");
        ideal.getStyleClass().add("form-control");
        Label hint = new Label("Cuando el stock llega al mínimo, SisTienda sugiere comprar hasta el ideal. Ideal 0 desactiva la alerta.");
        hint.setWrapText(true);
        hint.getStyleClass().add("dialog-subtitle");
        Label error = new Label();
        error.getStyleClass().add("form-error");
        VBox content = new VBox(8,
                field("Stock mínimo", minimo),
                field("Stock ideal", ideal),
                hint,
                error
        );
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog.getDialogPane());

        double[][] parsed = {null};
        Node save = dialog.getDialogPane().lookupButton(guardar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                double min = parseCantidad(minimo.getText());
                double target = parseCantidad(ideal.getText());
                if (min < 0 || target < 0) throw new IllegalArgumentException("Los niveles no pueden ser negativos.");
                if (target + EPSILON < min) throw new IllegalArgumentException("El stock ideal no puede ser menor al mínimo.");
                if (producto.unidadMedida() == UnidadMedida.UN
                        && (Math.abs(min - Math.rint(min)) > EPSILON || Math.abs(target - Math.rint(target)) > EPSILON)) {
                    throw new IllegalArgumentException("Este producto se maneja por unidad: usá cantidades enteras.");
                }
                parsed[0] = new double[]{min, target};
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == guardar ? parsed[0] : null);
        dialog.showAndWait().ifPresent(values -> {
            service.actualizarNiveles(producto, values[0], values[1]);
            mostrarFeedback("Niveles de reposición actualizados para “" + producto.nombre() + "”.");
            recargar();
        });
    }

    private void recargar() {
        items.setAll(service.listarPendientes());
        cantidad.setText(Integer.toString(items.size()));
        sinStock.setText(Long.toString(items.stream().filter(ReposicionItem::sinStock).count()));
        inversion.setText(formatCurrency(items.stream().mapToDouble(ReposicionItem::inversionEstimada).sum()));
        configurados.setText(Long.toString(service.contarConfigurados()));
        aplicarFiltro();
    }

    private void aplicarFiltro() {
        String query = buscar.getText() == null ? "" : buscar.getText().trim().toLowerCase(Locale.ROOT);
        filtrados.setPredicate(item -> query.isBlank()
                || item.producto().nombre().toLowerCase(Locale.ROOT).contains(query)
                || (item.producto().categoriaNombre() != null
                    && item.producto().categoriaNombre().toLowerCase(Locale.ROOT).contains(query)));
    }

    private void copiarLista() {
        if (items.isEmpty()) {
            mostrarFeedback("No hay productos pendientes de reposición.");
            return;
        }
        StringBuilder text = new StringBuilder("LISTA DE REPOSICIÓN · SisTienda\n\n");
        for (ReposicionItem item : items) {
            text.append("• ").append(item.producto().nombre())
                    .append(" — comprar ").append(formatCantidad(item.cantidadSugerida(), item.producto()))
                    .append(" (actual ").append(formatCantidad(item.producto().stockActual(), item.producto()))
                    .append(")\n");
        }
        text.append("\nInversión estimada: ").append(formatCurrency(items.stream().mapToDouble(ReposicionItem::inversionEstimada).sum()));
        ClipboardContent content = new ClipboardContent();
        content.putString(text.toString());
        Clipboard.getSystemClipboard().setContent(content);
        mostrarFeedback("Lista copiada. Podés pegarla en WhatsApp, correo o notas.");
    }

    private VBox metricCard(String title, Label value, String hint) {
        Label t = new Label(title);
        t.getStyleClass().add("metric-title");
        Label h = new Label(hint);
        h.getStyleClass().add("metric-hint");
        VBox box = new VBox(5, t, value, h);
        box.getStyleClass().add("metric-card");
        box.setPadding(new Insets(14, 16, 14, 16));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Label metricValue() {
        Label label = new Label("0");
        label.getStyleClass().add("metric-value");
        return label;
    }

    private VBox field(String title, TextField field) {
        Label label = new Label(title);
        label.getStyleClass().add("form-label");
        field.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, field);
    }

    private double parseCantidad(String value) {
        if (value == null || value.isBlank()) return 0d;
        String normalized = value.trim().replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double result = Double.parseDouble(normalized);
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingresá una cantidad válida.");
        }
    }

    private String formatCantidad(double value, Producto producto) {
        String number = producto.unidadMedida() == UnidadMedida.UN
                ? Long.toString(Math.round(value))
                : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        return number + (producto.unidadMedida() == UnidadMedida.KG ? " kg" : " un.");
    }

    private String formatInput(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void applyStyles(DialogPane pane) {
        var app = ReposicionView.class.getResource("/styles/app.css");
        if (app != null) pane.getStylesheets().add(app.toExternalForm());
        var css = ReposicionView.class.getResource("/styles/reposicion.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }
}
