package py.sistienda.ui.reportes;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaDetalleItem;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.service.ReporteService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ReportesView extends BorderPane {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReporteService reporteService;
    private final DatePicker fecha = new DatePicker(LocalDate.now());
    private final TableView<VentaResumen> tabla = new TableView<>();

    private final Label ventasValue = metricValueLabel();
    private final Label gananciaValue = metricValueLabel();
    private final Label ticketsValue = metricValueLabel();
    private final Label promedioValue = metricValueLabel();
    private final Label efectivoValue = paymentValueLabel();
    private final Label transferenciaValue = paymentValueLabel();
    private final Label tarjetaValue = paymentValueLabel();
    private final Label feedback = new Label();

    public ReportesView(ReporteService reporteService) {
        this.reporteService = reporteService;

        getStyleClass().add("content-area");
        setPadding(new Insets(20, 24, 20, 24));
        setTop(buildHeader());
        setCenter(buildContent());

        configurarTabla();
        fecha.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                recargar();
            }
        });
        recargar();
    }

    private HBox buildHeader() {
        Label eyebrow = new Label("CONTROL DEL NEGOCIO");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Reportes");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Ventas, ganancia y tickets del día en una sola vista.");
        subtitle.getStyleClass().add("page-subtitle");

        VBox heading = new VBox(3, eyebrow, title, subtitle);

        fecha.getStyleClass().add("report-date-picker");
        fecha.setPrefWidth(155);

        Button hoy = new Button("Hoy");
        hoy.getStyleClass().add("secondary-button");
        hoy.setOnAction(event -> fecha.setValue(LocalDate.now()));

        HBox dateControls = new HBox(8, fecha, hoy);
        dateControls.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, heading, spacer, dateControls);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));
        return header;
    }

    private VBox buildContent() {
        HBox metrics = new HBox(10,
                metricCard("VENTAS", ventasValue, "Facturación válida"),
                metricCard("GANANCIA", gananciaValue, "Margen registrado"),
                metricCard("TICKETS", ticketsValue, "Ventas realizadas"),
                metricCard("TICKET PROMEDIO", promedioValue, "Promedio por venta")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox payments = new HBox(10,
                paymentCard("EFECTIVO", efectivoValue),
                paymentCard("TRANSFERENCIA", transferenciaValue),
                paymentCard("TARJETA", tarjetaValue)
        );
        payments.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        feedback.getStyleClass().add("report-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);

        Label historyTitle = new Label("Historial de ventas");
        historyTitle.getStyleClass().add("report-section-title");
        Label historyHint = new Label("Abrí cualquier ticket para ver su detalle.");
        historyHint.getStyleClass().add("report-section-hint");

        HBox tableHeader = new HBox(8, historyTitle, historyHint);
        tableHeader.setAlignment(Pos.BASELINE_LEFT);
        tableHeader.setPadding(new Insets(10, 12, 7, 12));

        VBox historyCard = new VBox(0, tableHeader, feedback, tabla);
        historyCard.getStyleClass().add("report-history-card");
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox content = new VBox(10, metrics, payments, historyCard);
        VBox.setVgrow(historyCard, Priority.ALWAYS);
        return content;
    }

    private VBox metricCard(String title, Label value, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("report-metric-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("report-metric-hint");
        VBox card = new VBox(4, titleLabel, value, hintLabel);
        card.getStyleClass().add("report-metric-card");
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

    private void configurarTabla() {
        tabla.setPlaceholder(new Label("No hay ventas para esta fecha."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getStyleClass().add("report-table");

        TableColumn<VentaResumen, String> horaCol = new TableColumn<>("Hora");
        horaCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(TIME_FORMAT.format(cell.getValue().fecha())));
        horaCol.setPrefWidth(70);

        TableColumn<VentaResumen, String> ticketCol = new TableColumn<>("Ticket");
        ticketCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper("#" + cell.getValue().nroTicket()));
        ticketCol.setPrefWidth(80);

        TableColumn<VentaResumen, String> usuarioCol = new TableColumn<>("Usuario");
        usuarioCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().usuario()));
        usuarioCol.setPrefWidth(110);

        TableColumn<VentaResumen, String> pagoCol = new TableColumn<>("Pago");
        pagoCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().metodoPago().descripcion()));
        pagoCol.setPrefWidth(125);

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
            @Override
            protected void updateItem(VentaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                badge.getStyleClass().removeAll("report-status-ok", "report-status-cancelled");
                badge.setText(item.anulada() ? "Anulada" : "Válida");
                badge.getStyleClass().add(item.anulada() ? "report-status-cancelled" : "report-status-ok");
                setAlignment(Pos.CENTER);
                setGraphic(badge);
            }
        });

        TableColumn<VentaResumen, VentaResumen> actionCol = new TableColumn<>("");
        actionCol.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button detail = new Button("Ver ticket");
            {
                detail.getStyleClass().add("report-detail-button");
            }
            @Override
            protected void updateItem(VentaResumen item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                detail.setOnAction(event -> mostrarDetalle(item.id()));
                setAlignment(Pos.CENTER);
                setGraphic(detail);
            }
        });

        tabla.getColumns().setAll(horaCol, ticketCol, usuarioCol, pagoCol, totalCol, gananciaCol, estadoCol, actionCol);
        tabla.setRowFactory(view -> {
            var row = new javafx.scene.control.TableRow<VentaResumen>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    mostrarDetalle(row.getItem().id());
                }
            });
            return row;
        });
    }

    private void recargar() {
        ejecutar(() -> {
            LocalDate selected = fecha.getValue() == null ? LocalDate.now() : fecha.getValue();
            ReporteDiario resumen = reporteService.resumenDiario(selected);
            ventasValue.setText(formatCurrency(resumen.ventas()));
            gananciaValue.setText(formatCurrency(resumen.ganancia()));
            ticketsValue.setText(Long.toString(resumen.tickets()));
            promedioValue.setText(formatCurrency(resumen.ticketPromedio()));
            efectivoValue.setText(formatCurrency(resumen.efectivo()));
            transferenciaValue.setText(formatCurrency(resumen.transferencia()));
            tarjetaValue.setText(formatCurrency(resumen.tarjeta()));
            tabla.setItems(FXCollections.observableArrayList(reporteService.listarVentas(selected)));
        });
    }

    private void mostrarDetalle(long ventaId) {
        ejecutar(() -> {
            VentaDetalle detalle = reporteService.detalleVenta(ventaId);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Ticket #" + detalle.nroTicket());
            dialog.setHeaderText("Detalle de venta");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefWidth(680);
            dialog.getDialogPane().setPrefHeight(560);

            Label ticket = new Label("Ticket #" + detalle.nroTicket());
            ticket.getStyleClass().add("ticket-title");
            Label meta = new Label(DATE_TIME_FORMAT.format(detalle.fecha()) + "  ·  " + detalle.usuario()
                    + "  ·  " + detalle.metodoPago().descripcion());
            meta.getStyleClass().add("ticket-meta");

            TableView<VentaDetalleItem> items = new TableView<>();
            items.setItems(FXCollections.observableArrayList(detalle.items()));
            items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            items.getStyleClass().add("ticket-table");

            TableColumn<VentaDetalleItem, String> productCol = new TableColumn<>("Producto");
            productCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().producto()));
            productCol.setPrefWidth(250);
            TableColumn<VentaDetalleItem, String> qtyCol = new TableColumn<>("Cant.");
            qtyCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatQuantity(cell.getValue().cantidad())));
            qtyCol.setPrefWidth(80);
            TableColumn<VentaDetalleItem, String> priceCol = new TableColumn<>("Precio");
            priceCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().precioUnitario())));
            priceCol.setPrefWidth(110);
            TableColumn<VentaDetalleItem, String> subtotalCol = new TableColumn<>("Subtotal");
            subtotalCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatCurrency(cell.getValue().subtotal())));
            subtotalCol.setPrefWidth(120);
            items.getColumns().setAll(productCol, qtyCol, priceCol, subtotalCol);

            Label totalLabel = new Label("TOTAL  " + formatCurrency(detalle.total()));
            totalLabel.getStyleClass().add("ticket-total");
            Label gainLabel = new Label("Ganancia  " + formatCurrency(detalle.ganancia()));
            gainLabel.getStyleClass().add("ticket-gain");
            Label payment = new Label(detalle.metodoPago().descripcion()
                    + (detalle.metodoPago() == py.sistienda.core.model.MetodoPago.EFECTIVO
                    ? "  ·  Recibido " + formatCurrency(detalle.recibido()) + "  ·  Vuelto " + formatCurrency(detalle.vuelto())
                    : ""));
            payment.getStyleClass().add("ticket-payment");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox totals = new HBox(12, payment, spacer, gainLabel, totalLabel);
            totals.setAlignment(Pos.CENTER_LEFT);

            VBox content = new VBox(10, ticket, meta, items, totals);
            VBox.setVgrow(items, Priority.ALWAYS);
            content.setPadding(new Insets(4));
            dialog.getDialogPane().setContent(content);
            applyDialogStyles(dialog);
            dialog.showAndWait();
        });
    }

    private Label metricValueLabel() {
        Label label = new Label("Gs. 0");
        label.getStyleClass().add("report-metric-value");
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

    private String formatQuantity(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false);
            feedback.setManaged(false);
            action.run();
        } catch (RuntimeException e) {
            Throwable current = e;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            feedback.setText(current.getMessage() == null ? "No pudimos cargar el reporte." : current.getMessage());
            feedback.setVisible(true);
            feedback.setManaged(true);
        }
    }

    private void applyDialogStyles(Dialog<?> dialog) {
        addDialogStyle(dialog, "/styles/app.css");
        addDialogStyle(dialog, "/styles/reportes.css");
    }

    private void addDialogStyle(Dialog<?> dialog, String path) {
        var css = ReportesView.class.getResource(path);
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
    }
}
