package py.sistienda.ui.caja;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.ControlEfectivoCaja;
import py.sistienda.core.model.MovimientoCaja;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.ArqueoCajaService;
import py.sistienda.core.service.CajaService;
import py.sistienda.core.service.CodigoBarrasService;
import py.sistienda.core.service.ConfiguracionPosService;
import py.sistienda.core.service.EmpresaService;
import py.sistienda.core.service.MovimientoCajaService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.ReporteService;
import py.sistienda.core.service.VentaService;
import py.sistienda.ui.ticket.TicketDialog;
import py.sistienda.ui.venta.VentaView;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class CajaView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final CajaService cajaService;
    private final MovimientoCajaService movimientoCajaService;
    private final ArqueoCajaService arqueoCajaService;
    private final ProductoService productoService;
    private final VentaService ventaService;
    private final ReporteService reporteService;
    private final EmpresaService empresaService;
    private final ConfiguracionPosService configuracionPosService;
    private final CodigoBarrasService codigoBarrasService;
    private final Usuario usuario;

    private final VBox body = new VBox();
    private final Label feedback = new Label();
    private final Label ventasEfectivo = new Label("Gs. 0");
    private final Label ventasTransferencia = new Label("Gs. 0");
    private final Label ventasTarjeta = new Label("Gs. 0");
    private final Label ventasTotal = new Label("Gs. 0");
    private final Label movimientosIngresos = new Label("Gs. 0");
    private final Label movimientosEgresos = new Label("Gs. 0");
    private final Label efectivoEsperado = new Label("Gs. 0");

    public CajaView(
            CajaService cajaService,
            MovimientoCajaService movimientoCajaService,
            ArqueoCajaService arqueoCajaService,
            ProductoService productoService,
            VentaService ventaService,
            ReporteService reporteService,
            EmpresaService empresaService,
            ConfiguracionPosService configuracionPosService,
            CodigoBarrasService codigoBarrasService,
            Usuario usuario
    ) {
        this.cajaService = cajaService;
        this.movimientoCajaService = movimientoCajaService;
        this.arqueoCajaService = arqueoCajaService;
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.reporteService = reporteService;
        this.empresaService = empresaService;
        this.configuracionPosService = configuracionPosService;
        this.codigoBarrasService = codigoBarrasService == null ? new CodigoBarrasService() : codigoBarrasService;
        this.usuario = usuario;

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 20, 24));
        setTop(buildHeader());
        setCenter(body);
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("OPERACIÓN");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Caja & Ventas");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Escaneá, vendé y mantené controlado el efectivo real del turno.");
        subtitle.getStyleClass().add("page-subtitle");

        Button history = new Button("Historial de cajas");
        history.getStyleClass().add("secondary-button");
        history.setOnAction(event -> ejecutar(() -> HistorialCajasDialog.show(arqueoCajaService)));

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, title, subtitle, spacer, history);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(3, eyebrow, titleRow, feedback);
        header.setPadding(new Insets(0, 0, 10, 0));
        return header;
    }

    private void recargar() {
        body.getChildren().clear();
        body.setSpacing(8);
        body.setPadding(Insets.EMPTY);
        body.setAlignment(Pos.TOP_LEFT);
        cajaService.obtenerAbierta(usuario).ifPresentOrElse(this::mostrarCajaAbierta, this::mostrarApertura);
    }

    private void mostrarApertura() {
        body.setAlignment(Pos.TOP_CENTER);
        Label status = badge("Caja cerrada", "cash-status-closed");
        Label title = new Label("Abrí la caja para empezar a vender");
        title.getStyleClass().add("cash-title");
        Label subtitle = new Label("Registrá el fondo inicial. La apertura queda guardada para el cierre del turno.");
        subtitle.getStyleClass().add("cash-subtitle");
        subtitle.setWrapText(true);

        TextField apertura = new TextField();
        apertura.setPromptText("Ej.: 250000");
        apertura.getStyleClass().add("cash-input");
        TextField notas = new TextField();
        notas.setPromptText("Nota opcional");
        notas.getStyleClass().add("cash-input");

        Button abrir = new Button("Abrir caja y vender");
        abrir.getStyleClass().add("primary-button");
        abrir.setOnAction(event -> ejecutar(() -> {
            cajaService.abrir(usuario, parseMonto(apertura.getText(), "monto de apertura"), notas.getText());
            mostrarFeedback("Caja abierta correctamente.");
            recargar();
        }));

        HBox fields = new HBox(10, compactField("Fondo inicial (Gs.)", apertura), compactField("Nota", notas));
        fields.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox card = new VBox(12, status, title, subtitle, fields, abrir);
        card.getStyleClass().addAll("cash-main-card", "cash-open-card");
        card.setPadding(new Insets(22));
        card.setMaxWidth(760);
        body.getChildren().add(card);
    }

    private void mostrarCajaAbierta(CajaSesion sesion) {
        HBox statusBar = buildStatusBar(sesion);
        HBox resumen = buildSalesSummaryBar(sesion);
        HBox cashControl = buildCashControlBar(sesion);
        String prefijoPeso = configuracionPosService == null ? "20" : configuracionPosService.obtener().prefijoPeso();
        VentaView ventaView = new VentaView(
                productoService,
                ventaService,
                usuario,
                sesion,
                () -> {
                    actualizarResumenVentas(sesion);
                    actualizarControlEfectivo(sesion);
                    mostrarUltimoTicket();
                },
                codigoBarrasService,
                prefijoPeso
        );

        ventaView.setMinHeight(0);
        ventaView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(ventaView, Priority.ALWAYS);
        body.getChildren().addAll(statusBar, resumen, cashControl, ventaView);
        VBox.setVgrow(ventaView, Priority.ALWAYS);
    }

    private HBox buildStatusBar(CajaSesion sesion) {
        Label status = badge("Caja abierta", "cash-status-open");
        Label opened = new Label("Desde " + DATE_FORMAT.format(sesion.fechaApertura()));
        opened.getStyleClass().add("cash-bar-detail");
        Label fund = new Label("Fondo: " + formatCurrency(sesion.montoApertura()));
        fund.getStyleClass().add("cash-bar-detail");
        Label user = new Label("Usuario: " + usuario.username());
        user.getStyleClass().add("cash-bar-detail");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("Cerrar caja");
        close.getStyleClass().addAll("secondary-button", "cash-close-button");
        close.setOnAction(event -> mostrarDialogoCierre(sesion));
        HBox bar = new HBox(12, status, opened, separator(), fund, separator(), user, spacer, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("cash-status-bar");
        bar.setPadding(new Insets(8, 10, 8, 12));
        return bar;
    }

    private HBox buildSalesSummaryBar(CajaSesion sesion) {
        HBox bar = new HBox(8,
                salesMetric("EFECTIVO", ventasEfectivo, false),
                salesMetric("TRANSFERENCIA", ventasTransferencia, false),
                salesMetric("TARJETA", ventasTarjeta, false),
                salesMetric("TOTAL VENDIDO", ventasTotal, true)
        );
        bar.getStyleClass().add("cash-sales-summary");
        bar.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        actualizarResumenVentas(sesion);
        return bar;
    }

    private HBox buildCashControlBar(CajaSesion sesion) {
        Label ingresosTitle = new Label("+ INGRESOS");
        ingresosTitle.getStyleClass().add("cash-control-label");
        movimientosIngresos.getStyleClass().addAll("cash-control-value", "cash-control-income");
        Label egresosTitle = new Label("- EGRESOS");
        egresosTitle.getStyleClass().add("cash-control-label");
        movimientosEgresos.getStyleClass().addAll("cash-control-value", "cash-control-expense");
        Label esperadoTitle = new Label("EFECTIVO ESPERADO");
        esperadoTitle.getStyleClass().add("cash-control-label");
        efectivoEsperado.getStyleClass().addAll("cash-control-value", "cash-control-expected");

        HBox ingresos = compactMetric(ingresosTitle, movimientosIngresos);
        HBox egresos = compactMetric(egresosTitle, movimientosEgresos);
        HBox esperado = compactMetric(esperadoTitle, efectivoEsperado);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button movimientos = new Button("Movimientos");
        movimientos.getStyleClass().add("cash-movement-button");
        movimientos.setOnAction(event -> mostrarMovimientos(sesion));

        HBox bar = new HBox(14, ingresos, separator(), egresos, separator(), esperado, spacer, movimientos);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 12));
        bar.getStyleClass().add("cash-control-bar");
        actualizarControlEfectivo(sesion);
        return bar;
    }

    private HBox compactMetric(Label title, Label value) {
        HBox box = new HBox(6, title, value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private VBox salesMetric(String titleText, Label value, boolean totalMetric) {
        Label title = new Label(titleText);
        title.getStyleClass().add("cash-sales-label");
        value.getStyleClass().removeAll("cash-sales-value", "cash-sales-value-total");
        value.getStyleClass().add("cash-sales-value");
        if (totalMetric) value.getStyleClass().add("cash-sales-value-total");
        VBox card = new VBox(1, title, value);
        card.getStyleClass().add("cash-sales-metric");
        if (totalMetric) card.getStyleClass().add("cash-sales-metric-total");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void actualizarResumenVentas(CajaSesion sesion) {
        ResumenVentasCaja resumen = cajaService.resumenVentas(sesion);
        ventasEfectivo.setText(formatCurrency(resumen.efectivo()));
        ventasTransferencia.setText(formatCurrency(resumen.transferencia()));
        ventasTarjeta.setText(formatCurrency(resumen.tarjeta()));
        ventasTotal.setText(formatCurrency(resumen.total()));
    }

    private ControlEfectivoCaja actualizarControlEfectivo(CajaSesion sesion) {
        ResumenVentasCaja ventas = cajaService.resumenVentas(sesion);
        ControlEfectivoCaja control = movimientoCajaService.control(sesion, ventas.efectivo());
        movimientosIngresos.setText(formatCurrency(control.ingresos()));
        movimientosEgresos.setText(formatCurrency(control.egresos()));
        efectivoEsperado.setText(formatCurrency(control.efectivoEsperado()));
        return control;
    }

    private void mostrarMovimientos(CajaSesion sesion) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Movimientos de caja");
        dialog.setHeaderText("Ingresos y egresos del turno actual");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(820, 560);

        ObservableList<MovimientoCaja> items = FXCollections.observableArrayList(movimientoCajaService.listar(sesion));
        TableView<MovimientoCaja> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Todavía no hay movimientos manuales en esta caja."));
        table.getStyleClass().add("cash-movement-table");

        TableColumn<MovimientoCaja, String> hora = new TableColumn<>("Hora");
        hora.setCellValueFactory(cell -> new ReadOnlyStringWrapper(TIME_FORMAT.format(cell.getValue().fecha())));
        hora.setPrefWidth(70);
        TableColumn<MovimientoCaja, String> tipo = new TableColumn<>("Tipo");
        tipo.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().tipo() == TipoMovimientoCaja.INGRESO ? "Ingreso" : "Egreso"));
        tipo.setPrefWidth(85);
        TableColumn<MovimientoCaja, String> categoria = new TableColumn<>("Categoría");
        categoria.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().categoria()));
        categoria.setPrefWidth(130);
        TableColumn<MovimientoCaja, String> concepto = new TableColumn<>("Concepto");
        concepto.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().concepto()));
        concepto.setPrefWidth(240);
        TableColumn<MovimientoCaja, String> monto = new TableColumn<>("Monto");
        monto.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().monto())));
        monto.setPrefWidth(110);
        TableColumn<MovimientoCaja, String> user = new TableColumn<>("Usuario");
        user.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
        user.setPrefWidth(90);
        table.getColumns().setAll(hora, tipo, categoria, concepto, monto, user);

        Label resumen = new Label();
        resumen.getStyleClass().add("cash-movement-summary-label");
        Runnable refresh = () -> {
            items.setAll(movimientoCajaService.listar(sesion));
            var control = actualizarControlEfectivo(sesion);
            resumen.setText("Ingresos " + formatCurrency(control.ingresos())
                    + "   ·   Egresos " + formatCurrency(control.egresos())
                    + "   ·   Esperado " + formatCurrency(control.efectivoEsperado()));
        };
        refresh.run();

        Button ingreso = new Button("+ Ingreso");
        ingreso.getStyleClass().add("cash-income-button");
        ingreso.setOnAction(event -> { if (registrarMovimiento(sesion, TipoMovimientoCaja.INGRESO)) refresh.run(); });
        Button egreso = new Button("- Egreso / gasto");
        egreso.getStyleClass().add("cash-expense-button");
        egreso.setOnAction(event -> { if (registrarMovimiento(sesion, TipoMovimientoCaja.EGRESO)) refresh.run(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, resumen, spacer, ingreso, egreso);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, toolbar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        applyDialogStyles(dialog);
        dialog.showAndWait();
    }

    private boolean registrarMovimiento(CajaSesion sesion, TipoMovimientoCaja tipo) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(tipo == TipoMovimientoCaja.INGRESO ? "Registrar ingreso" : "Registrar egreso");
        dialog.setHeaderText(tipo == TipoMovimientoCaja.INGRESO ? "Dinero que entra a la caja" : "Dinero que sale de la caja");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Registrar");
        dialog.getDialogPane().setPrefWidth(470);

        ComboBox<String> categoria = new ComboBox<>(FXCollections.observableArrayList(categorias(tipo)));
        categoria.setValue(categorias(tipo).getFirst());
        categoria.setMaxWidth(Double.MAX_VALUE);
        TextField concepto = new TextField();
        concepto.setPromptText(tipo == TipoMovimientoCaja.INGRESO ? "Ej.: Aporte de efectivo" : "Ej.: Pago de flete");
        TextField monto = new TextField();
        monto.setPromptText("Monto en Gs.");
        TextField referencia = new TextField();
        referencia.setPromptText("Factura, recibo o referencia opcional");

        VBox content = new VBox(9,
                field("Categoría", categoria), field("Concepto", concepto),
                field("Monto (Gs.)", monto), field("Referencia", referencia)
        );
        dialog.getDialogPane().setContent(content);
        applyDialogStyles(dialog);

        boolean[] saved = {false};
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                movimientoCajaService.registrar(sesion, usuario, tipo, categoria.getValue(), concepto.getText(),
                        parseMonto(monto.getText(), "monto"), referencia.getText());
                saved[0] = true;
            } catch (RuntimeException e) {
                dialog.setHeaderText(rootMessage(e));
                event.consume();
            }
        });
        dialog.showAndWait();
        if (saved[0]) mostrarFeedback(tipo == TipoMovimientoCaja.INGRESO ? "Ingreso registrado." : "Egreso registrado.");
        return saved[0];
    }

    private List<String> categorias(TipoMovimientoCaja tipo) {
        if (tipo == TipoMovimientoCaja.INGRESO) return List.of("Ingreso extra", "Aporte", "Devolución", "Reintegro", "Otro");
        return List.of("Alquiler", "Luz", "Agua", "Internet", "Flete", "Compra menor", "Retiro", "Otro");
    }

    private void mostrarUltimoTicket() {
        var ventas = reporteService.listarVentas(LocalDate.now());
        if (ventas.isEmpty()) return;
        var ultima = ventas.getFirst();
        var config = configuracionPosService == null ? py.sistienda.core.model.ConfiguracionPos.porDefecto() : configuracionPosService.obtener();
        TicketDialog.show(empresaService.obtener(), reporteService.detalleVenta(ultima.id()), config);
    }

    private Region separator() {
        Region separator = new Region();
        separator.getStyleClass().add("cash-bar-separator");
        return separator;
    }

    private void mostrarDialogoCierre(CajaSesion sesion) {
        ControlEfectivoCaja control = actualizarControlEfectivo(sesion);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Cerrar caja");
        dialog.setHeaderText("Finalizar turno");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        TextField cierre = new TextField();
        cierre.setPromptText("Monto contado");
        cierre.getStyleClass().add("cash-input");
        TextField notas = new TextField();
        notas.setPromptText("Nota opcional");
        notas.getStyleClass().add("cash-input");

        Label esperado = new Label("Efectivo esperado: " + formatCurrency(control.efectivoEsperado()));
        esperado.getStyleClass().add("cash-close-expected");
        Label diferencia = new Label("Diferencia: —");
        diferencia.getStyleClass().add("cash-close-difference");
        cierre.textProperty().addListener((obs, oldValue, newValue) -> {
            try {
                if (newValue == null || newValue.isBlank()) {
                    diferencia.setText("Diferencia: —");
                    return;
                }
                double contado = parseMonto(newValue, "monto contado");
                double diff = contado - control.efectivoEsperado();
                diferencia.setText("Diferencia: " + (diff >= 0 ? "+" : "") + formatCurrency(diff));
            } catch (RuntimeException e) {
                diferencia.setText("Diferencia: —");
            }
        });

        Label hint = new Label("Contá el efectivo real. SisTienda compara el monto contra fondo + ventas en efectivo + ingresos - egresos.");
        hint.getStyleClass().add("cash-subtitle");
        hint.setWrapText(true);

        VBox content = new VBox(9,
                hint, esperado, diferencia,
                fieldLabel("Monto contado (Gs.)"), cierre,
                fieldLabel("Nota"), notas
        );
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(460);
        applyDialogStyles(dialog);

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> ejecutar(() -> {
            cajaService.cerrar(sesion, parseMonto(cierre.getText(), "monto de cierre"), notas.getText());
            mostrarFeedback("Caja cerrada correctamente. El arqueo quedó guardado en el historial.");
            recargar();
        }));
    }

    private VBox compactField(String labelText, TextField field) {
        Label label = fieldLabel(labelText);
        VBox box = new VBox(5, label, field);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox field(String labelText, Control control) {
        Label label = fieldLabel(labelText);
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(5, label, control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("cash-status", styleClass);
        return label;
    }

    private double parseMonto(String value, String campo) {
        if (value == null || value.isBlank()) throw new ValidationException("Ingresá el " + campo + ".");
        String normalized = value.trim().replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el " + campo + ". Usá sólo números.");
        }
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void ejecutar(Runnable action) {
        try {
            action.run();
        } catch (ValidationException e) {
            mostrarFeedback(e.getMessage());
        } catch (RuntimeException e) {
            mostrarFeedback(rootMessage(e));
        }
    }

    private void applyDialogStyles(Dialog<?> dialog) {
        addDialogStyle(dialog, "/styles/app.css");
        addDialogStyle(dialog, "/styles/caja.css");
    }

    private void addDialogStyle(Dialog<?> dialog, String path) {
        var css = CajaView.class.getResource(path);
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
