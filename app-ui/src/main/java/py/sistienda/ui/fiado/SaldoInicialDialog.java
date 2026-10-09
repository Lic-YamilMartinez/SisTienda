package py.sistienda.ui.fiado;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.MigracionClienteService;
import py.sistienda.core.service.VentaService;
import py.sistienda.ui.common.MoneyFieldSupport;
import py.sistienda.ui.common.ResponsiveDialogSupport;
import py.sistienda.ui.common.TooltipSupport;
import py.sistienda.ui.common.UserErrorMessages;

import java.time.LocalDate;
import java.util.Optional;

final class SaldoInicialDialog {

    private SaldoInicialDialog() {
    }

    static Optional<Cliente> show(
            Window owner,
            MigracionClienteService migracionService,
            VentaService ventaService,
            Usuario usuario,
            Cliente preseleccionado
    ) {
        Dialog<Cliente> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Cargar saldo inicial");
        dialog.setHeaderText("Registrar deuda anterior a SisTienda");

        ButtonType confirmar = new ButtonType("Cargar saldo inicial", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);

        var clientes = ventaService.buscarClientes(usuario, "").stream()
                .map(item -> item.cliente())
                .toList();

        ComboBox<Cliente> cliente = new ComboBox<>(FXCollections.observableArrayList(clientes));
        cliente.setMaxWidth(Double.MAX_VALUE);
        cliente.setPromptText("Seleccionar cliente...");
        if (preseleccionado != null) {
            cliente.getSelectionModel().select(
                    clientes.stream()
                            .filter(item -> item.id() == preseleccionado.id())
                            .findFirst()
                            .orElse(preseleccionado)
            );
        }

        TextField monto = new TextField();
        monto.setPromptText("Ej.: 120000");
        MoneyFieldSupport.install(monto);

        DatePicker fecha = new DatePicker(LocalDate.now());

        TextField referencia = new TextField("Cuaderno anterior");
        referencia.setPromptText("Ej.: Cuaderno anterior");

        TextField observacion = new TextField();
        observacion.setPromptText("Observación opcional");

        Label warning = new Label(
                "Este importe se incorporará como deuda histórica. "
                        + "No generará venta, ganancia, movimiento de stock ni entrada de dinero en caja."
        );
        warning.setWrapText(true);
        warning.getStyleClass().add("dialog-subtitle");

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        VBox content = new VBox(10,
                warning,
                field("Cliente", cliente),
                field("Saldo inicial (Gs.)", monto),
                field("Fecha de referencia", fecha),
                field("Referencia", referencia),
                field("Observación", observacion),
                error
        );
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        applyStyles(dialog.getDialogPane());
        ResponsiveDialogSupport.fit(dialog, 680, 620);

        TooltipSupport.install(monto,
                "Monto que el cliente ya debía antes de comenzar a usar SisTienda.");
        TooltipSupport.install(fecha,
                "Fecha de referencia de la deuda histórica; puede ser el día del cierre del cuaderno anterior.");

        Cliente[] selected = {null};
        Node save = dialog.getDialogPane().lookupButton(confirmar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                Cliente current = cliente.getValue();
                if (current == null) throw new ValidationException("Seleccioná un cliente.");
                migracionService.registrarSaldoInicial(
                        usuario,
                        current,
                        parseMoney(monto.getText()),
                        fecha.getValue(),
                        referencia.getText(),
                        observacion.getText()
                );
                selected[0] = current;
                error.setVisible(false);
                error.setManaged(false);
            } catch (RuntimeException e) {
                error.setText(UserErrorMessages.message(e));
                error.setVisible(true);
                error.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == confirmar ? selected[0] : null);
        return dialog.showAndWait();
    }

    private static VBox field(String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
    }

    private static double parseMoney(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Ingresá el saldo inicial.");
        }
        String normalized = value.trim()
                .replace("Gs.", "")
                .replace("Gs", "")
                .replace("₲", "")
                .replace(" ", "");
        if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(",", ".");
        else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) normalized = normalized.replace(".", "");
        try {
            double parsed = Double.parseDouble(normalized);
            if (!Double.isFinite(parsed) || parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new ValidationException("Ingresá un saldo inicial válido y mayor a cero.");
        }
    }

    private static void applyStyles(DialogPane pane) {
        var app = SaldoInicialDialog.class.getResource("/styles/app.css");
        if (app != null) pane.getStylesheets().add(app.toExternalForm());
        var venta = SaldoInicialDialog.class.getResource("/styles/venta.css");
        if (venta != null) pane.getStylesheets().add(venta.toExternalForm());
    }
}
