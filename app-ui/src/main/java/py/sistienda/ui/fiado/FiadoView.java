package py.sistienda.ui.fiado;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;
import py.sistienda.core.service.CajaService;
import py.sistienda.core.service.VentaService;
import py.sistienda.ui.venta.ClientesFiadoDialog;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class FiadoView extends BorderPane {
    private static final double EPSILON = 0.000001d;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final VentaService ventaService;
    private final CajaService cajaService;
    private final Usuario usuario;
    private final AutorizacionService autorizacionService;

    private final Label porCobrar = metricValue();
    private final Label deudores = metricValue();
    private final Label alDia = metricValue();
    private final Label feedback = new Label();
    private final TextField buscar = new TextField();
    private final TableView<ClienteCuentaResumen> tabla = new TableView<>();

    public FiadoView(VentaService ventaService, CajaService cajaService, Usuario usuario,
                     AutorizacionService autorizacionService) {
        this.ventaService = ventaService;
        this.cajaService = cajaService;
        this.usuario = usuario;
        this.autorizacionService = autorizacionService;
        autorizacionService.exigir(usuario, Permiso.FIADO_GESTIONAR);

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 20, 24));
        setTop(buildHeader());
        setCenter(buildContent());
        configurarTabla();
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("CUENTAS DE CLIENTES");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Clientes & Fiado");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Encontrá al cliente, mirá cuánto debe y cobrá su cuenta en segundos.");
        subtitle.getStyleClass().add("page-subtitle");

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button nuevo = new Button("+ Nuevo cliente");
        nuevo.getStyleClass().add("primary-button");
        nuevo.setOnAction(event -> {
            ClientesFiadoDialog.nuevoCliente(
                    getScene() == null ? null : getScene().getWindow(),
                    ventaService,
                    usuario,
                    buscar.getText()
            ).ifPresent(cliente -> {
                buscar.setText(cliente.nombre());
                recargar();
            });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, new VBox(2, title, subtitle), spacer, nuevo);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(3, eyebrow, row, feedback);
    }

    private VBox buildContent() {
        HBox metrics = new HBox(10,
                metricCard("POR COBRAR", porCobrar, "Total pendiente de todos los clientes"),
                metricCard("CLIENTES CON DEUDA", deudores, "Aparecen primero para cobrar más rápido"),
                metricCard("CUENTAS AL DÍA", alDia, "Clientes sin saldo pendiente")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        buscar.setPromptText("Buscar por nombre, teléfono o CI/RUC...");
        buscar.getStyleClass().add("pos-search");
        buscar.textProperty().addListener((obs, oldValue, newValue) -> recargarTabla());

        Label hint = new Label("Tip: escribí el nombre del cliente y tocá Cobrar. El monto aparece completo, pero podés registrar un abono parcial.");
        hint.getStyleClass().add("credit-hint");
        hint.setWrapText(true);

        VBox card = new VBox(10, buscar, hint, tabla);
        card.getStyleClass().add("pos-panel");
        card.setPadding(new Insets(14));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox body = new VBox(12, metrics, card);
        body.setPadding(new Insets(12, 0, 0, 0));
        VBox.setVgrow(card, Priority.ALWAYS);
        return body;
    }

    private void configurarTabla() {
        tabla.setPlaceholder(new Label("No encontramos clientes con ese criterio."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getStyleClass().add("report-table");

        TableColumn<ClienteCuentaResumen, String> cliente = new TableColumn<>("Cliente");
        cliente.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().cliente().nombre()));
        cliente.setPrefWidth(250);

        TableColumn<ClienteCuentaResumen, String> contacto = new TableColumn<>("Contacto");
        contacto.setCellValueFactory(cell -> new ReadOnlyStringWrapper(contacto(cell.getValue().cliente())));
        contacto.setPrefWidth(180);

        TableColumn<ClienteCuentaResumen, String> saldo = new TableColumn<>("Saldo");
        saldo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(saldoTexto(cell.getValue().saldo())));
        saldo.setPrefWidth(150);
        saldo.setCellFactory(column -> new TableCell<>() {
            private final Label value = new Label();
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) { setGraphic(null); return; }
                value.setText(text);
                value.getStyleClass().removeAll("credit-debt", "credit-ok", "credit-favor");
                ClienteCuentaResumen item = getTableRow() == null ? null : getTableRow().getItem();
                if (item != null && item.saldo() > EPSILON) value.getStyleClass().add("credit-debt");
                else if (item != null && item.saldo() < -EPSILON) value.getStyleClass().add("credit-favor");
                else value.getStyleClass().add("credit-ok");
                setGraphic(value);
            }
        });

        TableColumn<ClienteCuentaResumen, String> ultimo = new TableColumn<>("Último movimiento");
        ultimo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().ultimoMovimiento() == null ? "—" : DATE_TIME.format(cell.getValue().ultimoMovimiento())
        ));
        ultimo.setPrefWidth(155);

        TableColumn<ClienteCuentaResumen, ClienteCuentaResumen> acciones = new TableColumn<>("");
        acciones.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        acciones.setPrefWidth(205);
        acciones.setCellFactory(column -> new TableCell<>() {
            private final Button cobrar = new Button("Cobrar");
            private final Button detalle = new Button("Ver cuenta");
            private final HBox box = new HBox(6, cobrar, detalle);
            {
                cobrar.getStyleClass().add("primary-button");
                detalle.getStyleClass().add("secondary-button");
                box.setAlignment(Pos.CENTER);
            }
            @Override protected void updateItem(ClienteCuentaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                boolean tieneDeuda = item.saldo() > EPSILON;
                cobrar.setVisible(tieneDeuda);
                cobrar.setManaged(tieneDeuda);
                cobrar.setOnAction(event -> cobrarRapido(item.cliente()));
                detalle.setOnAction(event -> mostrarCuenta(item.cliente()));
                setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });

        tabla.getColumns().setAll(cliente, contacto, saldo, ultimo, acciones);
        tabla.setRowFactory(view -> {
            TableRow<ClienteCuentaResumen> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) mostrarCuenta(row.getItem().cliente());
            });
            return row;
        });
    }

    private void recargar() {
        ejecutar(() -> {
            List<ClienteCuentaResumen> todos = ventaService.buscarClientes(usuario, "");
            double total = todos.stream().mapToDouble(item -> Math.max(0d, item.saldo())).sum();
            long conDeuda = todos.stream().filter(ClienteCuentaResumen::tieneDeuda).count();
            long cuentasAlDia = todos.stream().filter(item -> Math.abs(item.saldo()) <= EPSILON).count();
            porCobrar.setText(formatCurrency(total));
            deudores.setText(Long.toString(conDeuda));
            alDia.setText(Long.toString(cuentasAlDia));
            recargarTabla();
        });
    }

    private void recargarTabla() {
        ejecutar(() -> tabla.setItems(FXCollections.observableArrayList(
                ventaService.buscarClientes(usuario, buscar.getText())
        )));
    }

    private void cobrarRapido(Cliente cliente) {
        ejecutar(() -> {
            CajaSesion caja = cajaService.obtenerAbierta(usuario)
                    .orElseThrow(() -> new ValidationException("Abrí una caja antes de cobrar un fiado."));
            double saldo = ventaService.saldoCliente(usuario, cliente.id());
            if (saldo <= EPSILON) throw new ValidationException("Este cliente ya no tiene deuda pendiente.");

            Dialog<ButtonType> dialog = new Dialog<>();
            if (getScene() != null) dialog.initOwner(getScene().getWindow());
            dialog.setTitle("Cobrar fiado");
            dialog.setHeaderText(cliente.nombre() + " · Debe " + formatCurrency(saldo));
            ButtonType confirmar = new ButtonType("Confirmar cobro", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(470);

            ComboBox<MetodoPago> metodo = new ComboBox<>(FXCollections.observableArrayList(
                    MetodoPago.EFECTIVO, MetodoPago.TRANSFERENCIA, MetodoPago.TARJETA
            ));
            metodo.setValue(MetodoPago.EFECTIVO);
            metodo.setMaxWidth(Double.MAX_VALUE);
            TextField monto = new TextField(BigDecimal.valueOf(saldo).stripTrailingZeros().toPlainString());
            TextField observacion = new TextField();
            observacion.setPromptText("Observación opcional");
            Label error = new Label();
            error.getStyleClass().add("form-error");
            error.setWrapText(true);

            VBox content = new VBox(9,
                    field("Forma de cobro", metodo),
                    field("Monto (Gs.)", monto),
                    new Label("Podés cobrar toda la deuda o registrar sólo un abono."),
                    field("Observación", observacion),
                    error
            );
            dialog.getDialogPane().setContent(content);
            applyStyles(dialog);

            Node save = dialog.getDialogPane().lookupButton(confirmar);
            save.addEventFilter(ActionEvent.ACTION, event -> {
                try {
                    ventaService.registrarAbonoCliente(
                            usuario, caja, cliente, metodo.getValue(), parseMoney(monto.getText()), observacion.getText()
                    );
                } catch (RuntimeException e) {
                    error.setText(rootMessage(e));
                    event.consume();
                }
            });

            if (dialog.showAndWait().filter(confirmar::equals).isPresent()) {
                mostrarFeedback("Cobro registrado para " + cliente.nombre() + ".");
                recargar();
            }
        });
    }

    private void mostrarCuenta(Cliente cliente) {
        ejecutar(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            if (getScene() != null) dialog.initOwner(getScene().getWindow());
            dialog.setTitle("Cuenta de " + cliente.nombre());
            dialog.setHeaderText(cliente.nombre());
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefSize(800, 620);

            Label saldo = new Label();
            saldo.getStyleClass().add("credit-account-balance");
            Button cobrar = new Button("Cobrar ahora");
            cobrar.getStyleClass().add("primary-button");
            TableView<ClienteCuentaMovimiento> movimientos = buildMovimientosTable();

            Runnable refresh = () -> {
                double actual = ventaService.saldoCliente(usuario, cliente.id());
                saldo.setText(actual > EPSILON ? "Debe " + formatCurrency(actual)
                        : actual < -EPSILON ? "Saldo a favor " + formatCurrency(-actual) : "Cuenta al día");
                cobrar.setDisable(actual <= EPSILON);
                movimientos.setItems(FXCollections.observableArrayList(
                        ventaService.movimientosCliente(usuario, cliente.id())
                ));
            };
            refresh.run();
            cobrar.setOnAction(event -> {
                cobrarRapido(cliente);
                refresh.run();
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(10, saldo, spacer, cobrar);
            top.setAlignment(Pos.CENTER_LEFT);
            VBox content = new VBox(12, top, movimientos);
            VBox.setVgrow(movimientos, Priority.ALWAYS);
            dialog.getDialogPane().setContent(content);
            applyStyles(dialog);
            dialog.showAndWait();
            recargar();
        });
    }

    private TableView<ClienteCuentaMovimiento> buildMovimientosTable() {
        TableView<ClienteCuentaMovimiento> table = new TableView<>();
        table.setPlaceholder(new Label("La cuenta todavía no tiene movimientos."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ClienteCuentaMovimiento, String> fecha = new TableColumn<>("Fecha");
        fecha.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().fecha())));
        fecha.setPrefWidth(135);
        TableColumn<ClienteCuentaMovimiento, String> tipo = new TableColumn<>("Movimiento");
        tipo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().tipo()));
        tipo.setPrefWidth(120);
        TableColumn<ClienteCuentaMovimiento, String> detalle = new TableColumn<>("Detalle");
        detalle.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().referencia() + " · " + cell.getValue().detalle()));
        detalle.setPrefWidth(285);
        TableColumn<ClienteCuentaMovimiento, String> cargo = new TableColumn<>("Cargo");
        cargo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().cargo() <= 0 ? "—" : formatCurrency(cell.getValue().cargo())));
        cargo.setPrefWidth(110);
        TableColumn<ClienteCuentaMovimiento, String> abono = new TableColumn<>("Abono");
        abono.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().abono() <= 0 ? "—" : formatCurrency(cell.getValue().abono())));
        abono.setPrefWidth(110);
        table.getColumns().setAll(fecha, tipo, detalle, cargo, abono);
        return table;
    }

    private static VBox metricCard(String title, Label value, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("pos-summary-label");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("credit-hint");
        hintLabel.setWrapText(true);
        VBox card = new VBox(3, titleLabel, value, hintLabel);
        card.getStyleClass().add("credit-metric-card");
        card.setPadding(new Insets(12));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label metricValue() {
        Label label = new Label("Gs. 0");
        label.getStyleClass().add("credit-metric-value");
        return label;
    }

    private VBox field(String text, javafx.scene.control.Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
    }

    private String contacto(Cliente cliente) {
        if (cliente.telefono() != null && !cliente.telefono().isBlank()) return cliente.telefono();
        if (cliente.documento() != null && !cliente.documento().isBlank()) return cliente.documento();
        return "—";
    }

    private String saldoTexto(double saldo) {
        if (saldo > EPSILON) return "Debe " + formatCurrency(saldo);
        if (saldo < -EPSILON) return "A favor " + formatCurrency(-saldo);
        return "Al día";
    }

    private double parseMoney(String value) {
        if (value == null || value.isBlank()) throw new ValidationException("Ingresá el monto del abono.");
        String normalized = value.trim().replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new ValidationException("Ingresá un monto válido.");
        }
    }

    private String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
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
        var app = FiadoView.class.getResource("/styles/app.css");
        if (app != null) dialog.getDialogPane().getStylesheets().add(app.toExternalForm());
        var venta = FiadoView.class.getResource("/styles/venta.css");
        if (venta != null) dialog.getDialogPane().getStylesheets().add(venta.toExternalForm());
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        if (getScene() != null) alert.initOwner(getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
