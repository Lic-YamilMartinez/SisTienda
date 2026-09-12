package py.sistienda.ui.venta;

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
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.VentaService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ClientesFiadoDialog {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ClientesFiadoDialog() {
    }

    public static Optional<Cliente> seleccionarCliente(
            Window owner,
            VentaService ventaService,
            Usuario usuario,
            double totalVenta
    ) {
        Dialog<Cliente> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Venta a crédito");
        dialog.setHeaderText("¿A quién le dejamos esta compra?");
        ButtonType usar = new ButtonType("Registrar fiado", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(usar, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(720, 560);

        Label amount = new Label(formatCurrency(totalVenta));
        amount.getStyleClass().add("credit-sale-total");
        Label hint = new Label("Buscá un cliente existente o crealo ahora. Sólo el nombre es obligatorio.");
        hint.setWrapText(true);
        hint.getStyleClass().add("credit-hint");

        TextField search = new TextField();
        search.setPromptText("Buscar por nombre, teléfono o CI/RUC...");
        search.getStyleClass().add("pos-control");
        Button nuevo = new Button("+ Nuevo cliente");
        nuevo.getStyleClass().add("secondary-button");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox toolbar = new HBox(8, search, nuevo);

        TableView<ClienteCuentaResumen> table = buildClientTable();
        Label selected = new Label("Seleccioná un cliente para continuar.");
        selected.getStyleClass().add("credit-selected-client");
        selected.setWrapText(true);

        Runnable reload = () -> {
            List<ClienteCuentaResumen> values = ventaService.buscarClientes(usuario, search.getText());
            table.setItems(FXCollections.observableArrayList(values));
        };
        reload.run();
        search.textProperty().addListener((obs, oldValue, newValue) -> reload.run());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
            if (value == null) {
                selected.setText("Seleccioná un cliente para continuar.");
            } else {
                selected.setText(value.cliente().nombre() + " · Saldo actual: " + formatBalance(value.saldo()));
            }
            dialog.getDialogPane().lookupButton(usar).setDisable(value == null);
        });
        dialog.getDialogPane().lookupButton(usar).setDisable(true);

        nuevo.setOnAction(event -> nuevoCliente(owner, ventaService, usuario, search.getText()).ifPresent(cliente -> {
            search.setText(cliente.nombre());
            reload.run();
            table.getItems().stream()
                    .filter(item -> item.cliente().id() == cliente.id())
                    .findFirst()
                    .ifPresent(item -> {
                        table.getSelectionModel().select(item);
                        table.scrollTo(item);
                    });
        }));

        VBox content = new VBox(10,
                new HBox(10, labeledValue("TOTAL A FIAR", amount)),
                hint,
                toolbar,
                table,
                selected
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);

        dialog.setResultConverter(button -> button == usar && table.getSelectionModel().getSelectedItem() != null
                ? table.getSelectionModel().getSelectedItem().cliente()
                : null);
        return dialog.showAndWait();
    }

    public static void gestionar(
            Window owner,
            VentaService ventaService,
            Usuario usuario,
            CajaSesion caja
    ) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Clientes & Fiado");
        dialog.setHeaderText("Cuentas corrientes de clientes");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(940, 680);

        Label porCobrar = metricValue();
        Label clientesDeudores = metricValue();
        Label saldoFavor = metricValue();

        HBox metrics = new HBox(9,
                metricCard("POR COBRAR", porCobrar, "Saldo pendiente de clientes"),
                metricCard("CON DEUDA", clientesDeudores, "Clientes con saldo pendiente"),
                metricCard("SALDO A FAVOR", saldoFavor, "Crédito originado por devoluciones")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        TextField search = new TextField();
        search.setPromptText("Buscar cliente...");
        search.getStyleClass().add("pos-control");
        Button nuevo = new Button("+ Nuevo cliente");
        nuevo.getStyleClass().add("primary-button");
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox toolbar = new HBox(8, search, nuevo);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        TableView<ClienteCuentaResumen> table = buildClientTable();
        TableColumn<ClienteCuentaResumen, ClienteCuentaResumen> action = new TableColumn<>("");
        action.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        action.setPrefWidth(115);
        action.setCellFactory(column -> new TableCell<>() {
            private final Button cuenta = new Button("Ver cuenta");
            { cuenta.getStyleClass().add("secondary-button"); }
            @Override protected void updateItem(ClienteCuentaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                cuenta.setOnAction(event -> mostrarCuenta(owner, ventaService, usuario, caja, item.cliente()));
                setAlignment(Pos.CENTER);
                setGraphic(cuenta);
            }
        });
        table.getColumns().add(action);
        table.setRowFactory(view -> {
            TableRow<ClienteCuentaResumen> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    mostrarCuenta(owner, ventaService, usuario, caja, row.getItem().cliente());
                }
            });
            return row;
        });

        Runnable reload = () -> {
            List<ClienteCuentaResumen> values = ventaService.buscarClientes(usuario, search.getText());
            table.setItems(FXCollections.observableArrayList(values));
            double debt = values.stream().mapToDouble(item -> Math.max(0d, item.saldo())).sum();
            long debtors = values.stream().filter(ClienteCuentaResumen::tieneDeuda).count();
            double favor = values.stream().mapToDouble(item -> Math.max(0d, -item.saldo())).sum();
            porCobrar.setText(formatCurrency(debt));
            clientesDeudores.setText(Long.toString(debtors));
            saldoFavor.setText(formatCurrency(favor));
        };
        reload.run();
        search.textProperty().addListener((obs, oldValue, newValue) -> reload.run());
        nuevo.setOnAction(event -> {
            nuevoCliente(owner, ventaService, usuario, search.getText());
            search.clear();
            reload.run();
        });

        Label cashHint = new Label(caja == null
                ? "Para registrar un abono, primero abrí una caja. Podés consultar cuentas igualmente."
                : "Los abonos en efectivo ingresan a esta caja, pero no se cuentan como una venta nueva.");
        cashHint.getStyleClass().add("credit-hint");
        cashHint.setWrapText(true);

        VBox content = new VBox(10, metrics, toolbar, table, cashHint);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);
        dialog.showAndWait();
    }

    public static Optional<Cliente> nuevoCliente(
            Window owner,
            VentaService ventaService,
            Usuario usuario,
            String nombreInicial
    ) {
        Dialog<Cliente> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Nuevo cliente");
        dialog.setHeaderText("Alta rápida de cliente");
        ButtonType guardar = new ButtonType("Guardar cliente", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(520);

        TextField nombre = new TextField(nombreInicial == null ? "" : nombreInicial.trim());
        nombre.setPromptText("Nombre y apellido / nombre comercial");
        TextField documento = new TextField();
        documento.setPromptText("CI o RUC opcional");
        TextField telefono = new TextField();
        telefono.setPromptText("Teléfono opcional");
        TextField direccion = new TextField();
        direccion.setPromptText("Dirección opcional");
        TextArea nota = new TextArea();
        nota.setPromptText("Referencia o nota opcional");
        nota.setPrefRowCount(2);
        nota.setWrapText(true);

        for (Control control : new Control[]{nombre, documento, telefono, direccion, nota}) {
            control.getStyleClass().add("pos-control");
            control.setMaxWidth(Double.MAX_VALUE);
        }

        Label hint = new Label("Sólo el nombre es obligatorio. Podés completar los demás datos si te sirven para identificar mejor al cliente.");
        hint.getStyleClass().add("credit-hint");
        hint.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        VBox content = new VBox(9,
                hint,
                field("Nombre *", nombre),
                new HBox(8, grow(field("CI / RUC", documento)), grow(field("Teléfono", telefono))),
                field("Dirección", direccion),
                field("Nota", nota),
                error
        );
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);

        Cliente[] saved = {null};
        Node save = dialog.getDialogPane().lookupButton(guardar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                saved[0] = ventaService.crearCliente(
                        usuario, nombre.getText(), documento.getText(), telefono.getText(), direccion.getText(), nota.getText()
                );
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == guardar ? saved[0] : null);
        return dialog.showAndWait();
    }

    private static void mostrarCuenta(
            Window owner,
            VentaService ventaService,
            Usuario usuario,
            CajaSesion caja,
            Cliente cliente
    ) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Cuenta · " + cliente.nombre());
        dialog.setHeaderText(cliente.nombre());
        ButtonType cerrar = ButtonType.CLOSE;
        dialog.getDialogPane().getButtonTypes().add(cerrar);
        dialog.getDialogPane().setPrefSize(780, 620);

        Label saldo = new Label();
        saldo.getStyleClass().add("credit-account-balance");
        Label contact = new Label(contactText(cliente));
        contact.getStyleClass().add("credit-hint");
        contact.setWrapText(true);
        Button cobrar = new Button("Registrar abono");
        cobrar.getStyleClass().add("primary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox accountHeader = new HBox(10, new VBox(3, contact, saldo), spacer, cobrar);
        accountHeader.setAlignment(Pos.CENTER_LEFT);

        TableView<ClienteCuentaMovimiento> movements = buildMovementTable();
        Runnable reload = () -> {
            double current = ventaService.saldoCliente(usuario, cliente.id());
            saldo.setText(balanceLabel(current));
            saldo.getStyleClass().removeAll("credit-debt", "credit-ok", "credit-favor");
            saldo.getStyleClass().add(current > 0.000001d ? "credit-debt" : current < -0.000001d ? "credit-favor" : "credit-ok");
            cobrar.setDisable(current <= 0.000001d);
            movements.setItems(FXCollections.observableArrayList(
                    ventaService.movimientosCliente(usuario, cliente.id())
            ));
        };
        reload.run();
        cobrar.setOnAction(event -> {
            double current = ventaService.saldoCliente(usuario, cliente.id());
            if (caja == null) {
                showMessage(owner, Alert.AlertType.INFORMATION, "Abrí una caja", "Necesitás una caja abierta para registrar el cobro.");
                return;
            }
            registrarAbono(owner, ventaService, usuario, caja, cliente, current).ifPresent(result -> reload.run());
        });

        VBox content = new VBox(12, accountHeader, movements);
        VBox.setVgrow(movements, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);
        dialog.showAndWait();
    }

    private static Optional<Long> registrarAbono(
            Window owner,
            VentaService ventaService,
            Usuario usuario,
            CajaSesion caja,
            Cliente cliente,
            double saldoActual
    ) {
        Dialog<Long> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Registrar abono");
        dialog.setHeaderText(cliente.nombre() + " · Debe " + formatCurrency(saldoActual));
        ButtonType registrar = new ButtonType("Confirmar cobro", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registrar, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(470);

        ComboBox<MetodoPago> metodo = new ComboBox<>(FXCollections.observableArrayList(
                MetodoPago.EFECTIVO, MetodoPago.TRANSFERENCIA, MetodoPago.TARJETA
        ));
        metodo.setValue(MetodoPago.EFECTIVO);
        metodo.setMaxWidth(Double.MAX_VALUE);
        TextField monto = new TextField(formatPlain(saldoActual));
        monto.setPromptText("Monto a cobrar");
        TextField observacion = new TextField();
        observacion.setPromptText("Observación opcional");
        for (Control control : new Control[]{metodo, monto, observacion}) {
            control.getStyleClass().add("pos-control");
            control.setMaxWidth(Double.MAX_VALUE);
        }

        Label hint = new Label("Si cobra en efectivo, el importe aumenta el efectivo esperado de la caja. El abono no genera una venta nueva.");
        hint.getStyleClass().add("credit-hint");
        hint.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        VBox content = new VBox(9,
                hint,
                field("Forma de cobro", metodo),
                field("Monto (Gs.)", monto),
                field("Observación", observacion),
                error
        );
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog);

        long[] id = {0};
        Node save = dialog.getDialogPane().lookupButton(registrar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                var result = ventaService.registrarAbonoCliente(
                        usuario, caja, cliente, metodo.getValue(), parseMoney(monto.getText()), observacion.getText()
                );
                id[0] = result.id();
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == registrar && id[0] > 0 ? id[0] : null);
        Optional<Long> result = dialog.showAndWait();
        if (result.isPresent()) {
            showMessage(owner, Alert.AlertType.INFORMATION, "Abono registrado",
                    "El cobro se guardó correctamente en la cuenta de " + cliente.nombre() + ".");
        }
        return result;
    }

    private static TableView<ClienteCuentaResumen> buildClientTable() {
        TableView<ClienteCuentaResumen> table = new TableView<>();
        table.setPlaceholder(new Label("No encontramos clientes con ese criterio."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ClienteCuentaResumen, String> name = new TableColumn<>("Cliente");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().cliente().nombre()));
        name.setPrefWidth(230);
        TableColumn<ClienteCuentaResumen, String> contact = new TableColumn<>("Contacto");
        contact.setCellValueFactory(cell -> new ReadOnlyStringWrapper(shortContact(cell.getValue().cliente())));
        contact.setPrefWidth(180);
        TableColumn<ClienteCuentaResumen, String> balance = new TableColumn<>("Saldo");
        balance.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatBalance(cell.getValue().saldo())));
        balance.setPrefWidth(145);
        TableColumn<ClienteCuentaResumen, String> last = new TableColumn<>("Último movimiento");
        last.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().ultimoMovimiento() == null ? "—" : DATE_TIME.format(cell.getValue().ultimoMovimiento())
        ));
        last.setPrefWidth(145);
        table.getColumns().setAll(name, contact, balance, last);
        return table;
    }

    private static TableView<ClienteCuentaMovimiento> buildMovementTable() {
        TableView<ClienteCuentaMovimiento> table = new TableView<>();
        table.setPlaceholder(new Label("La cuenta todavía no tiene movimientos."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ClienteCuentaMovimiento, String> date = new TableColumn<>("Fecha");
        date.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().fecha())));
        date.setPrefWidth(125);
        TableColumn<ClienteCuentaMovimiento, String> type = new TableColumn<>("Movimiento");
        type.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().tipo()));
        type.setPrefWidth(115);
        TableColumn<ClienteCuentaMovimiento, String> detail = new TableColumn<>("Detalle");
        detail.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().referencia() + " · " + cell.getValue().detalle()
        ));
        detail.setPrefWidth(260);
        TableColumn<ClienteCuentaMovimiento, String> charge = new TableColumn<>("Cargo");
        charge.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().cargo() <= 0 ? "—" : formatCurrency(cell.getValue().cargo())
        ));
        charge.setPrefWidth(105);
        TableColumn<ClienteCuentaMovimiento, String> payment = new TableColumn<>("Abono");
        payment.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().abono() <= 0 ? "—" : formatCurrency(cell.getValue().abono())
        ));
        payment.setPrefWidth(105);
        table.getColumns().setAll(date, type, detail, charge, payment);
        return table;
    }

    private static VBox metricCard(String title, Label value, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("pos-summary-label");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("credit-hint");
        hintLabel.setWrapText(true);
        VBox box = new VBox(3, titleLabel, value, hintLabel);
        box.getStyleClass().add("credit-metric-card");
        box.setPadding(new Insets(12));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Label metricValue() {
        Label value = new Label("Gs. 0");
        value.getStyleClass().add("credit-metric-value");
        return value;
    }

    private static VBox labeledValue(String title, Label value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("pos-summary-label");
        return new VBox(2, titleLabel, value);
    }

    private static VBox field(String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(5, label, control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static VBox grow(VBox node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }

    private static String shortContact(Cliente cliente) {
        if (cliente.telefono() != null && !cliente.telefono().isBlank()) return cliente.telefono();
        if (cliente.documento() != null && !cliente.documento().isBlank()) return cliente.documento();
        return "—";
    }

    private static String contactText(Cliente cliente) {
        StringBuilder text = new StringBuilder();
        if (cliente.documento() != null && !cliente.documento().isBlank()) text.append("CI/RUC: ").append(cliente.documento());
        if (cliente.telefono() != null && !cliente.telefono().isBlank()) {
            if (!text.isEmpty()) text.append(" · ");
            text.append("Tel: ").append(cliente.telefono());
        }
        if (cliente.direccion() != null && !cliente.direccion().isBlank()) {
            if (!text.isEmpty()) text.append(" · ");
            text.append(cliente.direccion());
        }
        return text.isEmpty() ? "Cliente sin datos adicionales" : text.toString();
    }

    private static String balanceLabel(double value) {
        if (value > 0.000001d) return "Debe " + formatCurrency(value);
        if (value < -0.000001d) return "Saldo a favor " + formatCurrency(-value);
        return "Cuenta al día";
    }

    private static String formatBalance(double value) {
        if (value > 0.000001d) return formatCurrency(value);
        if (value < -0.000001d) return "A favor " + formatCurrency(-value);
        return "Al día";
    }

    private static String formatCurrency(double value) {
        return "Gs. " + NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(value));
    }

    private static String formatPlain(double value) {
        return BigDecimal.valueOf(Math.max(0d, value)).stripTrailingZeros().toPlainString();
    }

    private static double parseMoney(String value) {
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

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private static void showMessage(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        applyStyles(alert);
        alert.showAndWait();
    }

    private static void applyStyles(Dialog<?> dialog) {
        addStyle(dialog, "/styles/app.css");
        addStyle(dialog, "/styles/venta.css");
    }

    private static void addStyle(Dialog<?> dialog, String path) {
        var css = ClientesFiadoDialog.class.getResource(path);
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
