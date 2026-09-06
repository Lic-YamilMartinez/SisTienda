package py.sistienda.ui.caja;

import javafx.collections.FXCollections;
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
import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.model.ControlEfectivoCaja;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;
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

public final class CajaOperativaView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CajaService cajaService;
    private final MovimientoCajaService movimientoCajaService;
    private final ProductoService productoService;
    private final VentaService ventaService;
    private final ReporteService reporteService;
    private final EmpresaService empresaService;
    private final ConfiguracionPosService configuracionPosService;
    private final CodigoBarrasService codigoBarrasService;
    private final AutorizacionService autorizacionService;
    private final Usuario usuario;

    private final VBox body = new VBox();
    private final Label feedback = new Label();
    private final Label ventasEfectivo = new Label("Gs. 0");
    private final Label ventasTransferencia = new Label("Gs. 0");
    private final Label ventasTarjeta = new Label("Gs. 0");
    private final Label ventasTotal = new Label("Gs. 0");

    public CajaOperativaView(
            CajaService cajaService,
            MovimientoCajaService movimientoCajaService,
            ProductoService productoService,
            VentaService ventaService,
            ReporteService reporteService,
            EmpresaService empresaService,
            ConfiguracionPosService configuracionPosService,
            CodigoBarrasService codigoBarrasService,
            AutorizacionService autorizacionService,
            Usuario usuario
    ) {
        this.cajaService = cajaService;
        this.movimientoCajaService = movimientoCajaService;
        this.productoService = productoService;
        this.ventaService = ventaService;
        this.reporteService = reporteService;
        this.empresaService = empresaService;
        this.configuracionPosService = configuracionPosService;
        this.codigoBarrasService = codigoBarrasService;
        this.autorizacionService = autorizacionService;
        this.usuario = usuario;

        autorizacionService.exigir(usuario, Permiso.CAJA_OPERAR);
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
        Label subtitle = new Label(usuario.rolUsuario().descripcion()
                + " · acceso operativo sin reportes financieros ni historial de arqueos.");
        subtitle.getStyleClass().add("page-subtitle");
        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);
        return new VBox(3, eyebrow, new HBox(10, title, subtitle), feedback);
    }

    private void recargar() {
        body.getChildren().clear();
        body.setSpacing(8);
        body.setAlignment(Pos.TOP_LEFT);
        cajaService.obtenerAbierta(usuario).ifPresentOrElse(this::mostrarCajaAbierta, this::mostrarApertura);
    }

    private void mostrarApertura() {
        body.setAlignment(Pos.TOP_CENTER);
        Label status = badge("Caja cerrada", "cash-status-closed");
        Label title = new Label("Abrí tu caja para empezar a vender");
        title.getStyleClass().add("cash-title");
        Label subtitle = new Label("El fondo inicial y el cierre quedan asociados a tu usuario.");
        subtitle.getStyleClass().add("cash-subtitle");

        TextField apertura = new TextField();
        apertura.setPromptText("Ej.: 250000");
        apertura.getStyleClass().add("cash-input");
        TextField notas = new TextField();
        notas.setPromptText("Nota opcional");
        notas.getStyleClass().add("cash-input");

        Button abrir = new Button("Abrir caja y vender");
        abrir.getStyleClass().add("primary-button");
        abrir.setOnAction(event -> ejecutar(() -> {
            autorizacionService.exigir(usuario, Permiso.CAJA_OPERAR);
            cajaService.abrir(usuario, parseMonto(apertura.getText(), "monto de apertura"), notas.getText());
            mostrarFeedback("Caja abierta correctamente.");
            recargar();
        }));

        HBox fields = new HBox(10, field("Fondo inicial (Gs.)", apertura), field("Nota", notas));
        fields.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox card = new VBox(12, status, title, subtitle, fields, abrir);
        card.getStyleClass().addAll("cash-main-card", "cash-open-card");
        card.setPadding(new Insets(22));
        card.setMaxWidth(760);
        body.getChildren().add(card);
    }

    private void mostrarCajaAbierta(CajaSesion sesion) {
        HBox status = buildStatusBar(sesion);
        HBox resumen = buildSummary(sesion);
        VentaView venta = new VentaView(
                productoService,
                ventaService,
                usuario,
                sesion,
                () -> {
                    actualizarResumen(sesion);
                    mostrarUltimoTicket();
                },
                codigoBarrasService,
                configuracionPosService.obtener().prefijoPeso()
        );
        venta.setMinHeight(0);
        venta.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(venta, Priority.ALWAYS);
        body.getChildren().addAll(status, resumen, venta);
        VBox.setVgrow(venta, Priority.ALWAYS);
    }

    private HBox buildStatusBar(CajaSesion sesion) {
        Label status = badge("Caja abierta", "cash-status-open");
        Label opened = new Label("Desde " + DATE_FORMAT.format(sesion.fechaApertura()));
        opened.getStyleClass().add("cash-bar-detail");
        Label fund = new Label("Fondo: " + formatCurrency(sesion.montoApertura()));
        fund.getStyleClass().add("cash-bar-detail");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button movimientos = new Button("Movimientos");
        movimientos.getStyleClass().add("cash-movement-button");
        boolean puedeMovimientos = autorizacionService.puede(usuario, Permiso.CAJA_MOVIMIENTOS);
        movimientos.setVisible(puedeMovimientos);
        movimientos.setManaged(puedeMovimientos);
        movimientos.setOnAction(event -> mostrarMovimiento(sesion));

        Button close = new Button("Cerrar caja");
        close.getStyleClass().addAll("secondary-button", "cash-close-button");
        close.setOnAction(event -> mostrarCierre(sesion));

        HBox bar = new HBox(12, status, opened, separator(), fund, spacer, movimientos, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("cash-status-bar");
        bar.setPadding(new Insets(8, 10, 8, 12));
        return bar;
    }

    private HBox buildSummary(CajaSesion sesion) {
        HBox bar = new HBox(8,
                metric("EFECTIVO", ventasEfectivo, false),
                metric("TRANSFERENCIA", ventasTransferencia, false),
                metric("TARJETA", ventasTarjeta, false),
                metric("TOTAL VENDIDO", ventasTotal, true)
        );
        bar.getStyleClass().add("cash-sales-summary");
        bar.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        actualizarResumen(sesion);
        return bar;
    }

    private VBox metric(String titleText, Label value, boolean total) {
        Label title = new Label(titleText);
        title.getStyleClass().add("cash-sales-label");
        value.getStyleClass().removeAll("cash-sales-value", "cash-sales-value-total");
        value.getStyleClass().add("cash-sales-value");
        if (total) value.getStyleClass().add("cash-sales-value-total");
        VBox card = new VBox(1, title, value);
        card.getStyleClass().add("cash-sales-metric");
        if (total) card.getStyleClass().add("cash-sales-metric-total");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void actualizarResumen(CajaSesion sesion) {
        ResumenVentasCaja resumen = cajaService.resumenVentas(sesion);
        ventasEfectivo.setText(formatCurrency(resumen.efectivo()));
        ventasTransferencia.setText(formatCurrency(resumen.transferencia()));
        ventasTarjeta.setText(formatCurrency(resumen.tarjeta()));
        ventasTotal.setText(formatCurrency(resumen.total()));
    }

    private void mostrarMovimiento(CajaSesion sesion) {
        ejecutar(() -> autorizacionService.exigir(usuario, Permiso.CAJA_MOVIMIENTOS));
        if (!autorizacionService.puede(usuario, Permiso.CAJA_MOVIMIENTOS)) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Movimiento de caja");
        dialog.setHeaderText("Registrar ingreso o egreso del turno");
        ButtonType guardar = new ButtonType("Registrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        ComboBox<TipoMovimientoCaja> tipo = new ComboBox<>(FXCollections.observableArrayList(TipoMovimientoCaja.values()));
        tipo.setValue(TipoMovimientoCaja.EGRESO);
        ComboBox<String> categoria = new ComboBox<>();
        categoria.setMaxWidth(Double.MAX_VALUE);
        Runnable cargarCategorias = () -> {
            List<String> values = tipo.getValue() == TipoMovimientoCaja.INGRESO
                    ? List.of("Ingreso extra", "Aporte", "Devolución", "Reintegro", "Otro")
                    : List.of("Alquiler", "Luz", "Agua", "Internet", "Flete", "Compra menor", "Retiro", "Otro");
            categoria.getItems().setAll(values);
            categoria.setValue(values.getFirst());
        };
        tipo.valueProperty().addListener((obs, oldValue, newValue) -> cargarCategorias.run());
        cargarCategorias.run();

        TextField concepto = new TextField();
        concepto.setPromptText("Ej.: Pago de flete");
        TextField monto = new TextField();
        monto.setPromptText("Monto en Gs.");
        TextField referencia = new TextField();
        referencia.setPromptText("Referencia opcional");
        Label error = new Label();
        error.getStyleClass().add("form-error");

        VBox content = new VBox(8,
                field("Tipo", tipo), field("Categoría", categoria), field("Concepto", concepto),
                field("Monto (Gs.)", monto), field("Referencia", referencia), error
        );
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        applyStyle(dialog);

        Node save = dialog.getDialogPane().lookupButton(guardar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                autorizacionService.exigir(usuario, Permiso.CAJA_MOVIMIENTOS);
                movimientoCajaService.registrar(sesion, usuario, tipo.getValue(), categoria.getValue(),
                        concepto.getText(), parseMonto(monto.getText(), "monto"), referencia.getText());
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            }
        });
        dialog.showAndWait().filter(guardar::equals).ifPresent(result -> mostrarFeedback("Movimiento registrado."));
    }

    private void mostrarCierre(CajaSesion sesion) {
        autorizacionService.exigir(usuario, Permiso.CAJA_OPERAR);
        ResumenVentasCaja ventas = cajaService.resumenVentas(sesion);
        ControlEfectivoCaja control = movimientoCajaService.control(sesion, ventas.efectivo());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Cerrar caja");
        dialog.setHeaderText("Finalizar turno de " + usuario.username());
        ButtonType cerrar = new ButtonType("Cerrar caja", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(cerrar, ButtonType.CANCEL);

        Label esperado = new Label("Efectivo esperado: " + formatCurrency(control.efectivoEsperado()));
        esperado.getStyleClass().add("cash-close-expected");
        TextField contado = new TextField();
        contado.setPromptText("Monto contado");
        TextField notas = new TextField();
        notas.setPromptText("Nota opcional");
        Label error = new Label();
        error.getStyleClass().add("form-error");

        VBox content = new VBox(9, esperado, field("Efectivo contado (Gs.)", contado), field("Nota", notas), error);
        content.setPrefWidth(450);
        dialog.getDialogPane().setContent(content);
        applyStyle(dialog);

        Node close = dialog.getDialogPane().lookupButton(cerrar);
        close.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                autorizacionService.exigir(usuario, Permiso.CAJA_OPERAR);
                cajaService.cerrar(sesion, parseMonto(contado.getText(), "monto contado"), notas.getText());
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            }
        });
        dialog.showAndWait().filter(cerrar::equals).ifPresent(result -> {
            mostrarFeedback("Caja cerrada correctamente.");
            recargar();
        });
    }

    private void mostrarUltimoTicket() {
        var ventas = reporteService.listarVentas(LocalDate.now());
        if (ventas.isEmpty()) return;
        var ultima = ventas.getFirst();
        ConfiguracionPos config = configuracionPosService.obtener();
        TicketDialog.show(empresaService.obtener(), reporteService.detalleVenta(ultima.id()), config);
    }

    private VBox field(String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
    }

    private Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("cash-status", styleClass);
        return label;
    }

    private Region separator() {
        Region separator = new Region();
        separator.getStyleClass().add("cash-bar-separator");
        return separator;
    }

    private double parseMonto(String value, String campo) {
        if (value == null || value.isBlank()) throw new ValidationException("Ingresá el " + campo + ".");
        String normalized = value.trim().replace("Gs.", "").replace("Gs", "").replace("₲", "").replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double result = Double.parseDouble(normalized);
            if (!Double.isFinite(result) || result < 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            throw new ValidationException("Revisá el " + campo + ". Usá sólo números.");
        }
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private void mostrarFeedback(String message) {
        feedback.setText(message);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }

    private void ejecutar(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            mostrarFeedback(rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause().getMessage() != null) current = current.getCause();
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private void applyStyle(Dialog<?> dialog) {
        addStyle(dialog, "/styles/app.css");
        addStyle(dialog, "/styles/caja.css");
    }

    private void addStyle(Dialog<?> dialog, String path) {
        var css = CajaOperativaView.class.getResource(path);
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
