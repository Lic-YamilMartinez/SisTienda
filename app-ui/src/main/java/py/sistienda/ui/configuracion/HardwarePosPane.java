package py.sistienda.ui.configuracion;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.service.ConfiguracionPosService;

public final class HardwarePosPane extends VBox {
    private final ConfiguracionPosService service;
    private final TextField prefijoPeso = new TextField();
    private final ComboBox<Integer> anchoTicket = new ComboBox<>(FXCollections.observableArrayList(58, 80));
    private final TextField anchoEtiqueta = new TextField();
    private final CheckBox imprimirAutomatico = new CheckBox("Abrir impresión del ticket al cobrar");
    private final Label feedback = new Label();

    public HardwarePosPane(ConfiguracionPosService service) {
        this.service = service;
        getStyleClass().add("config-card");
        setPadding(new Insets(18));
        setSpacing(10);
        build();
        cargar();
    }

    private void build() {
        Label title = new Label("Hardware POS");
        title.getStyleClass().add("config-section-title");
        Label hint = new Label("Prepará lector USB, balanza etiquetadora e impresoras. El lector funciona como teclado; la balanza usa EAN-13 con PLU y peso.");
        hint.getStyleClass().add("config-hint");
        hint.setWrapText(true);

        prefijoPeso.setPromptText("20");
        anchoEtiqueta.setPromptText("58");
        prefijoPeso.getStyleClass().add("config-input");
        anchoTicket.getStyleClass().add("config-input");
        anchoEtiqueta.getStyleClass().add("config-input");
        prefijoPeso.setMaxWidth(Double.MAX_VALUE);
        anchoTicket.setMaxWidth(Double.MAX_VALUE);
        anchoEtiqueta.setMaxWidth(Double.MAX_VALUE);

        VBox prefijoField = field("Prefijo balanza", prefijoPeso);
        VBox ticketField = field("Ticket (mm)", anchoTicket);
        VBox etiquetaField = field("Etiqueta (mm)", anchoEtiqueta);
        HBox row = new HBox(8, prefijoField, ticketField, etiquetaField);
        row.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        Label format = new Label("Formato peso: PP + PLU(5) + gramos(5) + dígito EAN-13. Ej.: prefijo 20.");
        format.getStyleClass().add("config-hint");
        format.setWrapText(true);

        Button save = new Button("Guardar hardware");
        save.getStyleClass().add("secondary-button");
        save.setOnAction(event -> guardar());

        feedback.getStyleClass().add("config-feedback");
        feedback.setVisible(false);
        feedback.setManaged(false);

        getChildren().addAll(title, hint, row, imprimirAutomatico, format, save, feedback);
    }

    private VBox field(String labelText, javafx.scene.control.Control control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        VBox box = new VBox(4, label, control);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void cargar() {
        ejecutar(() -> {
            ConfiguracionPos config = service.obtener();
            prefijoPeso.setText(config.prefijoPeso());
            anchoTicket.setValue(config.anchoTicketMm());
            anchoEtiqueta.setText(String.valueOf(config.anchoEtiquetaMm()));
            imprimirAutomatico.setSelected(config.imprimirTicketAutomatico());
        });
    }

    private void guardar() {
        ejecutar(() -> {
            int etiqueta;
            try {
                etiqueta = Integer.parseInt(anchoEtiqueta.getText().trim());
            } catch (Exception e) {
                throw new ValidationException("Revisá el ancho de etiqueta.");
            }
            ConfiguracionPos saved = service.guardar(prefijoPeso.getText(), anchoTicket.getValue(), etiqueta,
                    imprimirAutomatico.isSelected());
            prefijoPeso.setText(saved.prefijoPeso());
            anchoTicket.setValue(saved.anchoTicketMm());
            anchoEtiqueta.setText(String.valueOf(saved.anchoEtiquetaMm()));
            mostrar("Configuración POS guardada.");
        });
    }

    private void ejecutar(Runnable action) {
        try {
            feedback.setVisible(false);
            feedback.setManaged(false);
            action.run();
        } catch (RuntimeException e) {
            Throwable current = e;
            while (current.getCause() != null) current = current.getCause();
            mostrar(current.getMessage() == null ? "No se pudo guardar la configuración POS." : current.getMessage());
        }
    }

    private void mostrar(String text) {
        feedback.setText(text);
        feedback.setVisible(true);
        feedback.setManaged(true);
    }
}
