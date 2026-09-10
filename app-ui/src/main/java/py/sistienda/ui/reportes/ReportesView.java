package py.sistienda.ui.reportes;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.model.DashboardReporte;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.ProductoVendidoResumen;
import py.sistienda.core.model.ReporteLineaTiempo;
import py.sistienda.core.model.ReportePeriodoResumen;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.service.ConfiguracionPosService;
import py.sistienda.core.service.EmpresaService;
import py.sistienda.core.service.ReporteService;
import py.sistienda.ui.ticket.TicketDialog;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class ReportesView extends BorderPane {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yy", new Locale("es", "PY"));

    private final ReporteService reporteService;
    private final EmpresaService empresaService;
    private final ConfiguracionPosService configuracionPosService;

    private final ComboBox<String> periodo = new ComboBox<>();
    private final DatePicker desde = new DatePicker();
    private final DatePicker hasta = new DatePicker();
    private final ComboBox<OpcionPago> pago = new ComboBox<>();

    private final Label ventasValue = metricValueLabel();
    private final Label costoValue = metricValueLabel();
    private final Label gananciaValue = metricValueLabel();
    private final Label resultadoValue = metricValueLabel();
    private final Label ticketsValue = secondaryValueLabel();
    private final Label promedioValue = secondaryValueLabel();
    private final Label ingresosValue = secondaryValueLabel();
    private final Label egresosValue = secondaryValueLabel();
    private final Label efectivoValue = paymentValueLabel();
    private final Label transferenciaValue = paymentValueLabel();
    private final Label tarjetaValue = paymentValueLabel();
    private final Label resultadoHint = new Label();
    private final Label filterSummary = new Label();
    private final Label feedback = new Label();

    private final CategoryAxis timelineX = new CategoryAxis();
    private final NumberAxis timelineY = new NumberAxis();
    private final LineChart<String, Number> timeline = new LineChart<>(timelineX, timelineY);
    private final TableView<ProductoVendidoResumen> productos = new TableView<>();
    private final TableView<VentaResumen> ventas = new TableView<>();

    public ReportesView(ReporteService reporteService, EmpresaService empresaService) {
        this(reporteService, empresaService, null);
    }

    public ReportesView(ReporteService reporteService, EmpresaService empresaService,
                        ConfiguracionPosService configuracionPosService) {
        this.reporteService = reporteService;
        this.empresaService = empresaService;
        this.configuracionPosService = configuracionPosService;

        getStyleClass().add("content-area");
        setPadding(new Insets(18, 24, 18, 24));
        configurarFiltros();
        configurarTimeline();
        configurarProductos();
        configurarVentas();
        setTop(buildHeader());
        setCenter(buildTabs());
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("CONTROL DEL NEGOCIO");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Dashboard & Reportes");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Entendé qué vendiste, cómo cobraste y cuánto te dejó el negocio.");
        subtitle.getStyleClass().add("page-subtitle");
        VBox heading = new VBox(2, eyebrow, title, subtitle);

        filterSummary.getStyleClass().add("report-filter-summary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, heading, spacer, filterSummary);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        periodo.setPrefWidth(145);
        desde.setPrefWidth(135);
        hasta.setPrefWidth(135);
        pago.setPrefWidth(150);
        Button aplicar = new Button("Aplicar");
        aplicar.getStyleClass().add("primary-button");
        aplicar.setOnAction(event -> recargar());

        Button hoy = new Button("Hoy");
        hoy.getStyleClass().add("secondary-button");
        hoy.setOnAction(event -> {
            periodo.setValue("Hoy");
            aplicarPeriodo("Hoy");
            recargar();
        });

        HBox filters = new HBox(8,
                filterField("Período", periodo),
                filterField("Desde", desde),
                filterField("Hasta", hasta),
                filterField("Medio de pago", pago),
                aplicar, hoy
        );
        filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.getStyleClass().add("report-filter-bar");
        filters.setPadding(new Insets(10, 12, 10, 12));

        feedback.getStyleClass().add("report-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);

        VBox header = new VBox(10, titleRow, filters, feedback);
        header.setPadding(new Insets(0, 0, 10, 0));
        return header;
    }

    private TabPane buildTabs() {
        Tab dashboard = new Tab("Dashboard", buildDashboard());
        dashboard.setClosable(false);
        Tab sales = new Tab("Ventas del período", buildSalesHistory());
        sales.setClosable(false);

        TabPane tabs = new TabPane(dashboard, sales);
        tabs.getStyleClass().add("report-tabs");
        return tabs;
    }

    private ScrollPane buildDashboard() {
        HBox primaryMetrics = new HBox(10,
                metricCard("FACTURACIÓN", ventasValue, "Ventas válidas según el filtro"),
                metricCard("COSTO MERCADERÍA", costoValue, "Costo congelado al momento de vender"),
                metricCard("GANANCIA COMERCIAL", gananciaValue, "Facturación - costo de mercadería"),
                metricCard("RESULTADO NETO", resultadoValue, resultadoHint)
        );
        primaryMetrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox secondaryMetrics = new HBox(10,
                compactCard("TICKETS", ticketsValue),
                compactCard("TICKET PROMEDIO", promedioValue),
                compactCard("OTROS INGRESOS", ingresosValue),
                compactCard("EGRESOS / GASTOS", egresosValue)
        );
        secondaryMetrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox payments = new HBox(10,
                paymentCard("EFECTIVO", efectivoValue),
                paymentCard("TRANSFERENCIA", transferenciaValue),
                paymentCard("TARJETA", tarjetaValue)
        );
        payments.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox chartCard = sectionCard("Evolución del período",
                "Facturación y ganancia comercial. En un año se agrupa automáticamente por mes.", timeline);
        VBox productCard = sectionCard("Productos que más facturaron",
                "Top 10 del período. Cantidad, ventas, costo y ganancia real por producto.", productos);
        chartCard.setMinWidth(520);
        productCard.setMinWidth(430);
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        HBox.setHgrow(productCard, Priority.ALWAYS);
        HBox analysis = new HBox(10, chartCard, productCard);
        analysis.setAlignment(Pos.TOP_LEFT);

        VBox body = new VBox(10, primaryMetrics, secondaryMetrics, payments, analysis);
        body.setPadding(new Insets(10, 2, 14, 2));

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("report-dashboard-scroll");
        return scroll;
    }

    private VBox buildSalesHistory() {
        Label title = new Label("Detalle de ventas");
        title.getStyleClass().add("report-section-title");
        Label hint = new Label("Hasta 500 tickets del período seleccionado. Las anuladas se conservan para trazabilidad.");
        hint.getStyleClass().add("report-section-hint");
        HBox header = new HBox(8, title, hint);
        header.setAlignment(Pos.BASELINE_LEFT);
        header.setPadding(new Insets(10, 12, 7, 12));

        VBox card = new VBox(0, header, ventas);
        card.getStyleClass().add("report-history-card");
        VBox.setVgrow(ventas, Priority.ALWAYS);
        return card;
    }

    private void configurarFiltros() {
        periodo.setItems(FXCollections.observableArrayList(
                "Hoy", "Este mes", "Mes anterior", "Este año", "Personalizado"
        ));
        periodo.setValue("Este mes");
        periodo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !"Personalizado".equals(newValue)) {
                aplicarPeriodo(newValue);
                recargar();
            }
        });

        pago.setItems(FXCollections.observableArrayList(
                new OpcionPago("Todos", null),
                new OpcionPago("Efectivo", MetodoPago.EFECTIVO),
                new OpcionPago("Transferencia", MetodoPago.TRANSFERENCIA),
                new OpcionPago("Tarjeta", MetodoPago.TARJETA)
        ));
        pago.setValue(pago.getItems().getFirst());
        pago.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) recargar();
        });

        aplicarPeriodo("Este mes");
    }

    private void aplicarPeriodo(String value) {
        LocalDate hoy = LocalDate.now();
        switch (value) {
            case "Hoy" -> {
                desde.setValue(hoy);
                hasta.setValue(hoy);
            }
            case "Este mes" -> {
                desde.setValue(hoy.withDayOfMonth(1));
                hasta.setValue(hoy);
            }
            case "Mes anterior" -> {
                YearMonth anterior = YearMonth.from(hoy).minusMonths(1);
                desde.setValue(anterior.atDay(1));
                hasta.setValue(anterior.atEndOfMonth());
            }
            case "Este año" -> {
                desde.setValue(hoy.withDayOfYear(1));
                hasta.setValue(hoy);
            }
            default -> {
            }
        }
    }

    private void configurarTimeline() {
        timeline.setAnimated(false);
        timeline.setCreateSymbols(false);
        timeline.setLegendVisible(true);
        timeline.setMinHeight(310);
        timeline.setPrefHeight(330);
        timeline.getStyleClass().add("report-line-chart");
        timelineX.setLabel("Período");
        timelineY.setLabel("Gs.");
        timelineY.setForceZeroInRange(true);
        timelineY.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) {
                double amount = value.doubleValue();
                if (Math.abs(amount) >= 1_000_000) return String.format(Locale.US, "%.1fM", amount / 1_000_000d);
                if (Math.abs(amount) >= 1_000) return String.format(Locale.US, "%.0fk", amount / 1_000d);
                return Long.toString(Math.round(amount));
            }
            @Override public Number fromString(String string) { return 0; }
        });
    }

    private void configurarProductos() {
        productos.setPlaceholder(new Label("No hay productos vendidos en este período."));
        productos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        productos.getStyleClass().add("report-product-table");
        productos.setPrefHeight(330);
        productos.setMinHeight(310);

        TableColumn<ProductoVendidoResumen, String> product = new TableColumn<>("Producto");
        product.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto()));
        product.setPrefWidth(155);

        TableColumn<ProductoVendidoResumen, String> qty = new TableColumn<>("Cant.");
        qty.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQuantity(cell.getValue())));
        qty.setPrefWidth(70);

        TableColumn<ProductoVendidoResumen, String> sales = new TableColumn<>("Ventas");
        sales.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().ventas())));
        sales.setPrefWidth(100);

        TableColumn<ProductoVendidoResumen, String> gain = new TableColumn<>("Ganancia");
        gain.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().ganancia())));
        gain.setPrefWidth(100);

        TableColumn<ProductoVendidoResumen, String> margin = new TableColumn<>("Margen");
        margin.setCellValueFactory(cell -> {
            double pct = cell.getValue().ventas() <= 0 ? 0 : (cell.getValue().ganancia() / cell.getValue().ventas()) * 100d;
            return new ReadOnlyStringWrapper(String.format(Locale.US, "%.1f%%", pct));
        });
        margin.setPrefWidth(65);

        productos.getColumns().setAll(product, qty, sales, gain, margin);
    }

    private void configurarVentas() {
        ventas.setPlaceholder(new Label("No hay ventas para el período seleccionado."));
        ventas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ventas.getStyleClass().add("report-table");

        TableColumn<VentaResumen, String> fechaCol = new TableColumn<>("Fecha");
        fechaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(DATE_TIME.format(cell.getValue().fecha())));
        fechaCol.setPrefWidth(105);
        TableColumn<VentaResumen, String> ticketCol = new TableColumn<>("Ticket");
        ticketCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper("#" + cell.getValue().nroTicket()));
        ticketCol.setPrefWidth(75);
        TableColumn<VentaResumen, String> usuarioCol = new TableColumn<>("Usuario");
        usuarioCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
        usuarioCol.setPrefWidth(110);
        TableColumn<VentaResumen, String> pagoCol = new TableColumn<>("Pago");
        pagoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().metodoPago().descripcion()));
        pagoCol.setPrefWidth(120);
        TableColumn<VentaResumen, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().total())));
        totalCol.setPrefWidth(120);
        TableColumn<VentaResumen, String> gananciaCol = new TableColumn<>("Ganancia");
        gananciaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().ganancia())));
        gananciaCol.setPrefWidth(120);

        TableColumn<VentaResumen, VentaResumen> estadoCol = new TableColumn<>("Estado");
        estadoCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        estadoCol.setPrefWidth(90);
        estadoCol.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(VentaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                badge.getStyleClass().removeAll("report-status-ok", "report-status-cancelled");
                badge.setText(item.anulada() ? "Anulada" : "Válida");
                badge.getStyleClass().add(item.anulada() ? "report-status-cancelled" : "report-status-ok");
                setAlignment(Pos.CENTER);
                setGraphic(badge);
            }
        });

        TableColumn<VentaResumen, VentaResumen> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(105);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button detail = new Button("Ver ticket");
            { detail.getStyleClass().add("report-detail-button"); }
            @Override protected void updateItem(VentaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                detail.setOnAction(event -> mostrarTicket(item.id()));
                setAlignment(Pos.CENTER);
                setGraphic(detail);
            }
        });

        ventas.getColumns().setAll(fechaCol, ticketCol, usuarioCol, pagoCol, totalCol, gananciaCol, estadoCol, actionCol);
        ventas.setRowFactory(view -> {
            var row = new javafx.scene.control.TableRow<VentaResumen>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) mostrarTicket(row.getItem().id());
            });
            return row;
        });
    }

    private void recargar() {
        ejecutar(() -> {
            LocalDate from = desde.getValue() == null ? LocalDate.now() : desde.getValue();
            LocalDate to = hasta.getValue() == null ? from : hasta.getValue();
            OpcionPago selectedPayment = pago.getValue();
            MetodoPago metodoPago = selectedPayment == null ? null : selectedPayment.metodoPago();
            DashboardReporte dashboard = reporteService.dashboard(from, to, metodoPago);
            ReportePeriodoResumen resumen = dashboard.resumen();

            ventasValue.setText(formatCurrency(resumen.ventas()));
            costoValue.setText(formatCurrency(resumen.costoMercaderia()));
            gananciaValue.setText(formatCurrency(resumen.gananciaComercial()));
            resultadoValue.setText(formatCurrency(resumen.resultadoNetoOperativo()));
            ticketsValue.setText(Long.toString(resumen.tickets()));
            promedioValue.setText(formatCurrency(resumen.ticketPromedio()));
            ingresosValue.setText(formatCurrency(resumen.otrosIngresos()));
            egresosValue.setText(formatCurrency(resumen.egresosOperativos()));
            efectivoValue.setText(formatCurrency(resumen.efectivo()));
            transferenciaValue.setText(formatCurrency(resumen.transferencia()));
            tarjetaValue.setText(formatCurrency(resumen.tarjeta()));

            boolean filtered = metodoPago != null;
            resultadoHint.setText(filtered
                    ? "Global del período · no cambia con el filtro de pago"
                    : "Ganancia comercial + otros ingresos - egresos registrados");
            filterSummary.setText(formatPeriod(from, to) + "  ·  " + (selectedPayment == null ? "Todos" : selectedPayment.label()));

            cargarTimeline(dashboard, from, to);
            productos.setItems(FXCollections.observableArrayList(dashboard.productosMasVendidos()));
            ventas.setItems(FXCollections.observableArrayList(dashboard.ventas()));
        });
    }

    private void cargarTimeline(DashboardReporte dashboard, LocalDate from, LocalDate to) {
        timeline.getData().clear();
        XYChart.Series<String, Number> salesSeries = new XYChart.Series<>();
        salesSeries.setName("Facturación");
        XYChart.Series<String, Number> gainSeries = new XYChart.Series<>();
        gainSeries.setName("Ganancia comercial");

        boolean monthly = ChronoUnit.DAYS.between(from, to) > 62;
        for (ReporteLineaTiempo point : dashboard.lineaTiempo()) {
            String label = monthly ? MONTH_LABEL.format(point.periodo()) : DAY_LABEL.format(point.periodo());
            salesSeries.getData().add(new XYChart.Data<>(label, point.ventas()));
            gainSeries.getData().add(new XYChart.Data<>(label, point.gananciaComercial()));
        }
        timeline.getData().addAll(salesSeries, gainSeries);
    }

    private VBox metricCard(String title, Label value, String hintText) {
        Label hint = new Label(hintText);
        return metricCard(title, value, hint);
    }

    private VBox metricCard(String title, Label value, Label hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("report-metric-title");
        hint.getStyleClass().add("report-metric-hint");
        hint.setWrapText(true);
        VBox card = new VBox(4, titleLabel, value, hint);
        card.getStyleClass().add("report-metric-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox compactCard(String title, Label value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("report-metric-title");
        VBox card = new VBox(3, titleLabel, value);
        card.getStyleClass().addAll("report-metric-card", "report-compact-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox paymentCard(String title, Label value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("report-payment-title");
        VBox card = new VBox(3, titleLabel, value);
        card.getStyleClass().add("report-payment-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox sectionCard(String title, String hint, javafx.scene.Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("report-section-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("report-section-hint");
        hintLabel.setWrapText(true);
        VBox header = new VBox(2, titleLabel, hintLabel);
        VBox card = new VBox(8, header, content);
        card.getStyleClass().add("report-analysis-card");
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    private VBox filterField(String labelText, javafx.scene.control.Control control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("report-filter-label");
        VBox box = new VBox(3, label, control);
        return box;
    }

    private void mostrarTicket(long ventaId) {
        ejecutar(() -> {
            ConfiguracionPos config = configuracionPosService == null
                    ? ConfiguracionPos.porDefecto()
                    : configuracionPosService.obtener();
            TicketDialog.show(empresaService.obtener(), reporteService.detalleVenta(ventaId), config);
        });
    }

    private Label metricValueLabel() {
        Label label = new Label("Gs. 0");
        label.getStyleClass().add("report-metric-value");
        return label;
    }

    private Label secondaryValueLabel() {
        Label label = new Label("Gs. 0");
        label.getStyleClass().add("report-secondary-value");
        return label;
    }

    private Label paymentValueLabel() {
        Label label = new Label("Gs. 0");
        label.getStyleClass().add("report-payment-value");
        return label;
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "PY"));
        return "Gs. " + format.format(Math.round(value));
    }

    private String formatQuantity(ProductoVendidoResumen item) {
        if (item.unidadMedida() == UnidadMedida.UN) {
            return NumberFormat.getIntegerInstance(new Locale("es", "PY")).format(Math.round(item.cantidad())) + " un";
        }
        return String.format(new Locale("es", "PY"), "%.3f kg", item.cantidad());
    }

    private String formatPeriod(LocalDate from, LocalDate to) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (from.equals(to)) return format.format(from);
        return format.format(from) + " → " + format.format(to);
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false);
            feedback.setManaged(false);
            action.run();
        } catch (RuntimeException e) {
            Throwable current = e;
            while (current.getCause() != null) current = current.getCause();
            feedback.setText(current.getMessage() == null ? "No pudimos cargar el reporte." : current.getMessage());
            feedback.setVisible(true);
            feedback.setManaged(true);
        }
    }

    private record OpcionPago(String label, MetodoPago metodoPago) {
        @Override public String toString() { return label; }
    }
}
