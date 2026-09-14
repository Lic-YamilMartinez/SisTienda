package py.sistienda.ui.catalogo;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import py.sistienda.core.model.ImportacionProductoValidacion;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.ImportacionProductoService;

import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

final class ImportacionProductosDialog {
    private static final ButtonType IMPORTAR = new ButtonType("Importar productos", ButtonBar.ButtonData.OK_DONE);

    private ImportacionProductosDialog() {
    }

    static void show(Window owner, ImportacionProductoService service, Usuario usuario, Runnable onImported) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Importar productos");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(IMPORTAR, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(980, 690);

        ObservableList<ImportacionProductoValidacion> rows = FXCollections.observableArrayList();
        Label archivo = new Label("Todavía no seleccionaste un archivo.");
        archivo.getStyleClass().add("dialog-subtitle");
        archivo.setWrapText(true);
        Label validas = metricValue("0");
        Label errores = metricValue("0");
        Label estado = new Label();
        estado.getStyleClass().add("form-error");
        estado.setWrapText(true);

        TableView<ImportacionProductoValidacion> table = buildTable(rows);
        Button elegir = new Button("Elegir Excel / CSV");
        elegir.getStyleClass().add("primary-button");
        Button plantilla = new Button("Descargar plantilla");
        plantilla.getStyleClass().add("secondary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, archivo, spacer, plantilla, elegir);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(archivo, Priority.ALWAYS);

        HBox metrics = new HBox(10,
                metricCard("LISTAS PARA IMPORTAR", validas, "Estas filas se guardarán"),
                metricCard("CON OBSERVACIONES", errores, "Se omiten hasta corregirlas")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        Label title = new Label("Carga masiva de productos");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("Usá la plantilla de SisTienda o tu propio Excel/CSV. Revisamos todo antes de tocar el catálogo.");
        subtitle.getStyleClass().add("dialog-subtitle");
        subtitle.setWrapText(true);
        Label note = new Label("Si hay filas con error podés importar igualmente las filas válidas; las observadas se omiten. Los códigos y PLU duplicados se detectan antes de guardar.");
        note.getStyleClass().add("dialog-subtitle");
        note.setWrapText(true);

        VBox content = new VBox(10, title, subtitle, actions, metrics, table, note, estado);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog.getDialogPane());

        Node importNode = dialog.getDialogPane().lookupButton(IMPORTAR);
        importNode.setDisable(true);

        Runnable refreshMetrics = () -> {
            long ok = rows.stream().filter(ImportacionProductoValidacion::valida).count();
            long bad = rows.size() - ok;
            validas.setText(Long.toString(ok));
            errores.setText(Long.toString(bad));
            importNode.setDisable(ok == 0);
            if (importNode instanceof Button button) button.setText(ok == 0 ? "Importar productos" : "Importar " + ok + " válidos");
        };

        elegir.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar productos para importar");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                    new FileChooser.ExtensionFilter("CSV", "*.csv", "*.txt")
            );
            File selected = chooser.showOpenDialog(owner);
            if (selected == null) return;
            try {
                estado.setText("");
                var raw = ProductoImportFileParser.leer(selected.toPath());
                List<ImportacionProductoValidacion> validated = service.validar(usuario, raw);
                rows.setAll(validated);
                archivo.setText(selected.getName() + " · " + raw.size() + " filas detectadas");
                refreshMetrics.run();
            } catch (RuntimeException e) {
                rows.clear();
                refreshMetrics.run();
                estado.setText(rootMessage(e));
                archivo.setText("No pudimos preparar el archivo seleccionado.");
            }
        });

        plantilla.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar plantilla SisTienda");
            chooser.setInitialFileName("Plantilla_Productos_SisTienda.xlsx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
            File selected = chooser.showSaveDialog(owner);
            if (selected == null) return;
            try {
                ProductoImportFileParser.crearPlantilla(selected.toPath());
                estado.setStyle("");
                estado.setText("Plantilla guardada en: " + selected.getAbsolutePath());
            } catch (RuntimeException e) {
                estado.setText(rootMessage(e));
            }
        });

        importNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                var result = service.importar(usuario, List.copyOf(rows));
                if (onImported != null) onImported.run();
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                        "Se crearon " + result.productosCreados() + " productos, "
                                + result.categoriasCreadas() + " categorías nuevas y "
                                + result.movimientosStock() + " movimientos de stock inicial.",
                        ButtonType.OK);
                if (owner != null) success.initOwner(owner);
                success.setTitle("Importación completada");
                success.setHeaderText("Productos cargados correctamente");
                applyStyles(success.getDialogPane());
                success.showAndWait();
            } catch (RuntimeException e) {
                estado.setText(rootMessage(e));
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private static TableView<ImportacionProductoValidacion> buildTable(ObservableList<ImportacionProductoValidacion> rows) {
        TableView<ImportacionProductoValidacion> table = new TableView<>(rows);
        table.setPlaceholder(new Label("Elegí un Excel o CSV para ver la vista previa."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ImportacionProductoValidacion, Number> fila = new TableColumn<>("Fila");
        fila.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().fila()));
        fila.setPrefWidth(65);

        TableColumn<ImportacionProductoValidacion, String> nombre = new TableColumn<>("Producto");
        nombre.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().nombre()));
        nombre.setPrefWidth(220);

        TableColumn<ImportacionProductoValidacion, String> unidad = new TableColumn<>("Unidad");
        unidad.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null ? "—" : cell.getValue().entrada().unidadMedida().name()));
        unidad.setPrefWidth(70);

        TableColumn<ImportacionProductoValidacion, String> precio = new TableColumn<>("Precio");
        precio.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null ? "—" : formatCurrency(cell.getValue().entrada().precioVenta())));
        precio.setPrefWidth(110);

        TableColumn<ImportacionProductoValidacion, String> stock = new TableColumn<>("Stock inicial");
        stock.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null ? "—" : formatNumber(cell.getValue().entrada().stockInicial())));
        stock.setPrefWidth(100);

        TableColumn<ImportacionProductoValidacion, ImportacionProductoValidacion> estado = new TableColumn<>("Validación");
        estado.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        estado.setPrefWidth(360);
        estado.setCellFactory(column -> new TableCell<>() {
            private final Label label = new Label();
            @Override protected void updateItem(ImportacionProductoValidacion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                label.setText(item.resumenErrores());
                label.setWrapText(true);
                label.getStyleClass().removeAll("status-ok", "status-empty");
                label.getStyleClass().add(item.valida() ? "status-ok" : "status-empty");
                setGraphic(label);
            }
        });

        table.getColumns().setAll(fila, nombre, unidad, precio, stock, estado);
        return table;
    }

    private static Label metricValue(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("metric-value");
        return label;
    }

    private static VBox metricCard(String title, Label value, String hint) {
        Label t = new Label(title);
        t.getStyleClass().add("metric-title");
        Label h = new Label(hint);
        h.getStyleClass().add("metric-hint");
        VBox box = new VBox(4, t, value, h);
        box.getStyleClass().add("metric-card");
        box.setPadding(new Insets(12));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001d) return Long.toString(Math.round(value));
        return String.format(Locale.US, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause().getMessage() != null) current = current.getCause();
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private static void applyStyles(DialogPane pane) {
        var app = ImportacionProductosDialog.class.getResource("/styles/app.css");
        if (app != null) pane.getStylesheets().add(app.toExternalForm());
    }
}
