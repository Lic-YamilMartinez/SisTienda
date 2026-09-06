package py.sistienda.ui.caja;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.ArqueoCajaDetalle;
import py.sistienda.core.model.ArqueoCajaResumen;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.MovimientoCaja;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.core.service.ArqueoCajaService;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class HistorialCajasDialog {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private HistorialCajasDialog() {
    }

    public static void show(ArqueoCajaService service) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Historial de cajas");
        dialog.setHeaderText("Cierres y arqueos de caja");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(1120, 680);

        var source = FXCollections.observableArrayList(service.listarRecientes());
        var filtered = new FilteredList<>(source, item -> true);
        TableView<ArqueoCajaResumen> table = new TableView<>(filtered);
        table.setPlaceholder(new Label("Todavía no hay sesiones de caja registradas."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("cash-reconciliation-table");

        TextField search = new TextField();
        search.setPromptText("Buscar por usuario...");
        search.setPrefWidth(220);

        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("Todas", "Cerradas", "Abiertas", "Con diferencia"));
        status.setValue("Todas");
        status.setPrefWidth(150);

        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            String selected = status.getValue();
            filtered.setPredicate(item -> {
                boolean textOk = q.isBlank() || item.usuario().toLowerCase(Locale.ROOT).contains(q);
                boolean stateOk = switch (selected == null ? "Todas" : selected) {
                    case "Cerradas" -> item.estado() == EstadoCaja.CERRADA;
                    case "Abiertas" -> item.estado() == EstadoCaja.ABIERTA;
                    case "Con diferencia" -> item.diferencia() != null && Math.abs(item.diferencia()) >= 0.5;
                    default -> true;
                };
                return textOk && stateOk;
            });
        };
        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());
        status.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter.run());

        TableColumn<ArqueoCajaResumen, String> cajaCol = new TableColumn<>("Caja");
        cajaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper("#" + cell.getValue().cajaId()));
        cajaCol.setPrefWidth(65);

        TableColumn<ArqueoCajaResumen, String> aperturaCol = new TableColumn<>("Apertura");
        aperturaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().fechaApertura())));
        aperturaCol.setPrefWidth(130);

        TableColumn<ArqueoCajaResumen, String> cierreCol = new TableColumn<>("Cierre");
        cierreCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().fechaCierre() == null ? "—" : DATE_TIME.format(cell.getValue().fechaCierre())));
        cierreCol.setPrefWidth(130);

        TableColumn<ArqueoCajaResumen, String> userCol = new TableColumn<>("Usuario");
        userCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
        userCol.setPrefWidth(90);

        TableColumn<ArqueoCajaResumen, String> vendidoCol = new TableColumn<>("Vendido");
        vendidoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(money(cell.getValue().totalVendido())));
        vendidoCol.setPrefWidth(115);

        TableColumn<ArqueoCajaResumen, String> esperadoCol = new TableColumn<>("Esperado");
        esperadoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(money(cell.getValue().efectivoEsperado())));
        esperadoCol.setPrefWidth(115);

        TableColumn<ArqueoCajaResumen, String> contadoCol = new TableColumn<>("Contado");
        contadoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().efectivoContado() == null ? "—" : money(cell.getValue().efectivoContado())));
        contadoCol.setPrefWidth(115);

        TableColumn<ArqueoCajaResumen, ArqueoCajaResumen> diffCol = new TableColumn<>("Diferencia");
        diffCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        diffCol.setPrefWidth(105);
        diffCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(ArqueoCajaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.diferencia() == null) {
                    setGraphic(null);
                    return;
                }
                badge.getStyleClass().removeAll("cash-diff-ok", "cash-diff-positive", "cash-diff-negative");
                double diff = item.diferencia();
                if (Math.abs(diff) < 0.5) {
                    badge.setText("Exacta");
                    badge.getStyleClass().add("cash-diff-ok");
                } else if (diff > 0) {
                    badge.setText("+" + money(diff));
                    badge.getStyleClass().add("cash-diff-positive");
                } else {
                    badge.setText(money(diff));
                    badge.getStyleClass().add("cash-diff-negative");
                }
                badge.getStyleClass().add("cash-diff-badge");
                setAlignment(Pos.CENTER);
                setGraphic(badge);
            }
        });

        TableColumn<ArqueoCajaResumen, String> estadoCol = new TableColumn<>("Estado");
        estadoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().estado() == EstadoCaja.ABIERTA ? "Abierta" : "Cerrada"));
        estadoCol.setPrefWidth(80);

        TableColumn<ArqueoCajaResumen, ArqueoCajaResumen> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button button = new Button("Ver arqueo");
            { button.getStyleClass().add("cash-history-detail-button"); }
            @Override protected void updateItem(ArqueoCajaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                button.setOnAction(event -> showDetail(service.detalle(item.cajaId())));
                setAlignment(Pos.CENTER);
                setGraphic(button);
            }
        });

        table.getColumns().setAll(cajaCol, aperturaCol, cierreCol, userCol, vendidoCol, esperadoCol,
                contadoCol, diffCol, estadoCol, actionCol);
        table.setRowFactory(view -> {
            TableRow<ArqueoCajaResumen> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showDetail(service.detalle(row.getItem().cajaId()));
                }
            });
            return row;
        });

        Label count = new Label();
        count.getStyleClass().add("cash-history-count");
        count.textProperty().bind(javafx.beans.binding.Bindings.size(filtered).asString("%d cajas"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, search, status, spacer, count);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, toolbar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);
        dialog.showAndWait();
    }

    private static void showDetail(ArqueoCajaDetalle item) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Arqueo caja #" + item.cajaId());
        dialog.setHeaderText("Caja #" + item.cajaId() + " · " + item.usuario());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(900, 680);

        Label period = new Label(DATE_TIME.format(item.fechaApertura()) + "  →  "
                + (item.fechaCierre() == null ? "En curso" : DATE_TIME.format(item.fechaCierre())));
        period.getStyleClass().add("cash-reconciliation-period");

        HBox row1 = new HBox(8,
                metric("FONDO INICIAL", money(item.fondoInicial()), false),
                metric("VENTA EFECTIVO", money(item.ventas().efectivo()), false),
                metric("INGRESOS", money(item.movimientos().ingresos()), false),
                metric("EGRESOS", money(item.movimientos().egresos()), false)
        );
        row1.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox row2 = new HBox(8,
                metric("EFECTIVO ESPERADO", money(item.efectivoEsperado()), true),
                metric("EFECTIVO CONTADO", item.efectivoContado() == null ? "—" : money(item.efectivoContado()), false),
                metric("DIFERENCIA", differenceText(item.diferencia()), false),
                metric("TOTAL VENDIDO", money(item.ventas().total()), false)
        );
        row2.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        Label sales = new Label("Ventas: Efectivo " + money(item.ventas().efectivo())
                + "   ·   Transferencia " + money(item.ventas().transferencia())
                + "   ·   Tarjeta " + money(item.ventas().tarjeta())
                + "   ·   Tickets " + item.tickets());
        sales.getStyleClass().add("cash-reconciliation-sales");

        TableView<MovimientoCaja> movements = new TableView<>(FXCollections.observableArrayList(item.detalleMovimientos()));
        movements.setPlaceholder(new Label("Sin movimientos manuales en este turno."));
        movements.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movements.getStyleClass().add("cash-movement-table");

        TableColumn<MovimientoCaja, String> timeCol = new TableColumn<>("Hora");
        timeCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(TIME.format(cell.getValue().fecha())));
        timeCol.setPrefWidth(70);
        TableColumn<MovimientoCaja, String> typeCol = new TableColumn<>("Tipo");
        typeCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().tipo() == TipoMovimientoCaja.INGRESO ? "Ingreso" : "Egreso"));
        typeCol.setPrefWidth(85);
        TableColumn<MovimientoCaja, String> categoryCol = new TableColumn<>("Categoría");
        categoryCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().categoria()));
        categoryCol.setPrefWidth(120);
        TableColumn<MovimientoCaja, String> conceptCol = new TableColumn<>("Concepto");
        conceptCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().concepto()));
        conceptCol.setPrefWidth(260);
        TableColumn<MovimientoCaja, String> amountCol = new TableColumn<>("Monto");
        amountCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(money(cell.getValue().monto())));
        amountCol.setPrefWidth(110);
        TableColumn<MovimientoCaja, String> userCol = new TableColumn<>("Usuario");
        userCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
        userCol.setPrefWidth(90);
        movements.getColumns().setAll(timeCol, typeCol, categoryCol, conceptCol, amountCol, userCol);

        Label notesTitle = new Label("OBSERVACIÓN DE CIERRE");
        notesTitle.getStyleClass().add("cash-control-label");
        Label notes = new Label(item.notas() == null || item.notas().isBlank() ? "Sin observaciones." : item.notas());
        notes.setWrapText(true);
        notes.getStyleClass().add("cash-reconciliation-notes");
        VBox notesBox = new VBox(3, notesTitle, notes);

        VBox content = new VBox(10, period, row1, row2, sales, new Separator(), movements, notesBox);
        VBox.setVgrow(movements, Priority.ALWAYS);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);
        dialog.showAndWait();
    }

    private static VBox metric(String titleText, String valueText, boolean highlight) {
        Label title = new Label(titleText);
        title.getStyleClass().add("cash-reconciliation-metric-title");
        Label value = new Label(valueText);
        value.getStyleClass().add("cash-reconciliation-metric-value");
        VBox card = new VBox(3, title, value);
        card.getStyleClass().add("cash-reconciliation-metric");
        if (highlight) card.getStyleClass().add("cash-reconciliation-metric-highlight");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static String differenceText(Double difference) {
        if (difference == null) return "—";
        if (Math.abs(difference) < 0.5) return "Caja exacta";
        return (difference > 0 ? "+" : "") + money(difference);
    }

    private static String money(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private static void applyStyles(Dialog<?> dialog) {
        addStyle(dialog, "/styles/app.css");
        addStyle(dialog, "/styles/caja.css");
    }

    private static void addStyle(Dialog<?> dialog, String path) {
        var css = HistorialCajasDialog.class.getResource(path);
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
