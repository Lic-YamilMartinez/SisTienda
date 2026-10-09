package py.sistienda.ui.fiado;

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
import py.sistienda.core.model.ImportacionClienteSaldoValidacion;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.MigracionClienteService;
import py.sistienda.ui.common.ResponsiveDialogSupport;
import py.sistienda.ui.common.UserErrorMessages;

import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

final class MigracionSaldosDialog {

    private static final ButtonType IMPORTAR = new ButtonType(
            "Migrar saldos", ButtonBar.ButtonData.OK_DONE
    );

    private MigracionSaldosDialog() {
    }

    static void show(
            Window owner,
            MigracionClienteService service,
            Usuario usuario,
            Runnable onImported
    ) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Migrar clientes y saldos iniciales");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(IMPORTAR, ButtonType.CANCEL);

        ObservableList<ImportacionClienteSaldoValidacion> rows = FXCollections.observableArrayList();
        Label archivo = new Label("Todavía no seleccionaste un archivo.");
        archivo.getStyleClass().add("dialog-subtitle");
        archivo.setWrapText(true);

        Label validas = metricValue("0");
        Label total = metricValue("Gs. 0");
        Label errores = metricValue("0");
        Label estado = new Label();
        estado.getStyleClass().add("form-error");
        estado.setWrapText(true);

        TableView<ImportacionClienteSaldoValidacion> table = buildTable(rows);

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
                metricCard("SALDOS LISTOS", validas, "Filas válidas para migrar"),
                metricCard("CARTERA A MIGRAR", total, "Deuda histórica total de filas válidas"),
                metricCard("CON OBSERVACIONES", errores, "Estas filas se omiten hasta corregirlas")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        Label title = new Label("Migración inicial de clientes");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label(
                "Cargá deudas anteriores a SisTienda sin generar ventas, ganancia ni movimientos de stock."
        );
        subtitle.getStyleClass().add("dialog-subtitle");
        subtitle.setWrapText(true);

        Label warning = new Label(
                "Importante: cada cliente puede tener un solo saldo inicial migrado. "
                        + "Si el documento o el nombre+teléfono ya existe, SisTienda vincula el saldo al cliente existente."
        );
        warning.getStyleClass().add("dialog-subtitle");
        warning.setWrapText(true);

        VBox content = new VBox(10, title, subtitle, actions, metrics, table, warning, estado);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        ResponsiveDialogSupport.scrollContent(dialog, content, 1040, 720);
        applyStyles(dialog.getDialogPane());

        Node importNode = dialog.getDialogPane().lookupButton(IMPORTAR);
        importNode.setDisable(true);

        String[] fileName = {null};

        Runnable refresh = () -> {
            long ok = rows.stream().filter(ImportacionClienteSaldoValidacion::valida).count();
            long bad = rows.size() - ok;
            double amount = rows.stream()
                    .filter(ImportacionClienteSaldoValidacion::valida)
                    .map(ImportacionClienteSaldoValidacion::entrada)
                    .mapToDouble(entry -> entry == null ? 0d : entry.saldoInicial())
                    .sum();

            validas.setText(Long.toString(ok));
            errores.setText(Long.toString(bad));
            total.setText(formatCurrency(amount));
            importNode.setDisable(ok == 0);
            if (importNode instanceof Button button) {
                button.setText(ok == 0 ? "Migrar saldos" : "Migrar " + ok + " saldos");
            }
        };

        elegir.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Seleccionar clientes y saldos iniciales");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                    new FileChooser.ExtensionFilter("CSV", "*.csv", "*.txt")
            );
            File selected = chooser.showOpenDialog(owner);
            if (selected == null) return;

            try {
                estado.setText("");
                var raw = ClienteSaldoImportFileParser.leer(selected.toPath());
                List<ImportacionClienteSaldoValidacion> validated = service.validar(usuario, raw);
                rows.setAll(validated);
                fileName[0] = selected.getName();
                archivo.setText(selected.getName() + " · " + raw.size() + " filas detectadas");
                refresh.run();
            } catch (RuntimeException e) {
                rows.clear();
                fileName[0] = null;
                refresh.run();
                estado.setText(UserErrorMessages.message(e));
                archivo.setText("No pudimos preparar el archivo seleccionado.");
            }
        });

        plantilla.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar plantilla de saldos iniciales");
            chooser.setInitialFileName("Plantilla_Clientes_Saldos_Iniciales_SisTienda.xlsx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
            File selected = chooser.showSaveDialog(owner);
            if (selected == null) return;
            try {
                ClienteSaldoImportFileParser.crearPlantilla(selected.toPath());
                estado.setText("Plantilla guardada en: " + selected.getAbsolutePath());
            } catch (RuntimeException e) {
                estado.setText(UserErrorMessages.message(e));
            }
        });

        importNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                var result = service.importar(
                        usuario,
                        List.copyOf(rows),
                        fileName[0] == null ? "Migración inicial" : fileName[0]
                );
                if (onImported != null) onImported.run();

                Alert success = new Alert(
                        Alert.AlertType.INFORMATION,
                        "Clientes creados: " + result.clientesCreados()
                                + "\nClientes existentes vinculados: " + result.clientesExistentes()
                                + "\nSaldos iniciales cargados: " + result.saldosInicialesCargados()
                                + "\nCartera migrada: " + formatCurrency(result.totalMigrado()),
                        ButtonType.OK
                );
                if (owner != null) success.initOwner(owner);
                success.setTitle("Migración completada");
                success.setHeaderText("Cartera inicial cargada correctamente");
                applyStyles(success.getDialogPane());
                success.showAndWait();
            } catch (RuntimeException e) {
                estado.setText(UserErrorMessages.message(e));
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private static TableView<ImportacionClienteSaldoValidacion> buildTable(
            ObservableList<ImportacionClienteSaldoValidacion> rows
    ) {
        TableView<ImportacionClienteSaldoValidacion> table = new TableView<>(rows);
        table.setPlaceholder(new Label("Elegí un Excel o CSV para ver la vista previa."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ImportacionClienteSaldoValidacion, Number> fila = new TableColumn<>("Fila");
        fila.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().fila()));
        fila.setPrefWidth(60);

        TableColumn<ImportacionClienteSaldoValidacion, String> cliente = new TableColumn<>("Cliente");
        cliente.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().cliente()));
        cliente.setPrefWidth(220);

        TableColumn<ImportacionClienteSaldoValidacion, String> documento = new TableColumn<>("CI / RUC");
        documento.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null || cell.getValue().entrada().documento() == null
                        ? "—" : cell.getValue().entrada().documento()
        ));
        documento.setPrefWidth(120);

        TableColumn<ImportacionClienteSaldoValidacion, String> saldo = new TableColumn<>("Saldo inicial");
        saldo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null
                        ? "—" : formatCurrency(cell.getValue().entrada().saldoInicial())
        ));
        saldo.setPrefWidth(120);

        TableColumn<ImportacionClienteSaldoValidacion, String> fecha = new TableColumn<>("Fecha ref.");
        fecha.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null
                        ? "—" : cell.getValue().entrada().fechaReferencia().toString()
        ));
        fecha.setPrefWidth(100);

        TableColumn<ImportacionClienteSaldoValidacion, String> destino = new TableColumn<>("Destino");
        destino.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().entrada() == null
                        ? "—"
                        : cell.getValue().entrada().clienteExistente()
                        ? "Cliente existente"
                        : "Nuevo cliente"
        ));
        destino.setPrefWidth(130);

        TableColumn<ImportacionClienteSaldoValidacion, ImportacionClienteSaldoValidacion> estado =
                new TableColumn<>("Validación");
        estado.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        estado.setPrefWidth(300);
        estado.setCellFactory(column -> new TableCell<>() {
            private final Label label = new Label();

            @Override
            protected void updateItem(ImportacionClienteSaldoValidacion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                label.setText(item.resumenErrores());
                label.setWrapText(true);
                label.getStyleClass().removeAll("status-ok", "status-empty");
                label.getStyleClass().add(item.valida() ? "status-ok" : "status-empty");
                setGraphic(label);
            }
        });

        table.getColumns().setAll(fila, cliente, documento, saldo, fecha, destino, estado);
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
        h.setWrapText(true);
        VBox box = new VBox(4, t, value, h);
        box.getStyleClass().add("metric-card");
        box.setPadding(new Insets(12));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY"))
                .format(Math.round(value));
    }

    private static void applyStyles(DialogPane pane) {
        var app = MigracionSaldosDialog.class.getResource("/styles/app.css");
        if (app != null) pane.getStylesheets().add(app.toExternalForm());
        var venta = MigracionSaldosDialog.class.getResource("/styles/venta.css");
        if (venta != null) pane.getStylesheets().add(venta.toExternalForm());
    }
}
