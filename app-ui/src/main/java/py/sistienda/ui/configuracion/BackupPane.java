package py.sistienda.ui.configuracion;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import py.sistienda.core.model.BackupInfo;
import py.sistienda.core.service.BackupService;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;

public final class BackupPane extends VBox {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final BackupService backupService;
    private final ComboBox<BackupInfo> backups = new ComboBox<>();
    private final Label status = new Label();

    public BackupPane(BackupService backupService) {
        this.backupService = backupService;
        getStyleClass().add("config-card");
        setSpacing(10);
        setFillWidth(true);

        getChildren().addAll(
                buildTitle(),
                pathLabel("Base", backupService.databaseFile().toString()),
                pathLabel("Backups", backupService.backupDirectory().toString()),
                buildBackupSelector(),
                buildActions(),
                status
        );

        status.getStyleClass().add("backup-status");
        status.setWrapText(true);
        refrescar();
    }

    private VBox buildTitle() {
        Label title = new Label("Protección de datos");
        title.getStyleClass().add("config-section-title");
        Label hint = new Label("Creá copias de seguridad y restaurá la base si alguna vez necesitás volver atrás.");
        hint.getStyleClass().add("config-hint");
        hint.setWrapText(true);
        return new VBox(3, title, hint);
    }

    private VBox pathLabel(String labelText, String valueText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("backup-path-label");
        Label value = new Label(valueText);
        value.getStyleClass().add("backup-path-value");
        value.setWrapText(true);
        return new VBox(2, label, value);
    }

    private VBox buildBackupSelector() {
        Label label = new Label("Backup disponible");
        label.getStyleClass().add("form-label");

        backups.setMaxWidth(Double.MAX_VALUE);
        backups.getStyleClass().add("config-input");
        backups.setConverter(new StringConverter<>() {
            @Override
            public String toString(BackupInfo info) {
                return info == null ? "" : format(info);
            }

            @Override
            public BackupInfo fromString(String string) {
                return null;
            }
        });
        backups.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(BackupInfo info, boolean empty) {
                super.updateItem(info, empty);
                setText(empty || info == null ? "" : format(info));
            }
        });
        return new VBox(5, label, backups);
    }

    private HBox buildActions() {
        Button crear = new Button("Crear backup ahora");
        crear.getStyleClass().add("primary-button");
        crear.setOnAction(event -> ejecutar(() -> {
            BackupInfo info = backupService.crearManual();
            refrescar();
            backups.setValue(info);
            status.setText("Backup creado: " + info.nombreArchivo());
        }));

        Button restaurar = new Button("Restaurar");
        restaurar.getStyleClass().add("secondary-button");
        restaurar.setOnAction(event -> confirmarRestauracion());

        Button carpeta = new Button("Abrir carpeta");
        carpeta.getStyleClass().add("secondary-button");
        carpeta.setOnAction(event -> ejecutar(this::abrirCarpeta));

        HBox row = new HBox(7, crear, restaurar, carpeta);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(crear, Priority.ALWAYS);
        return row;
    }

    private void confirmarRestauracion() {
        BackupInfo selected = backups.getValue();
        if (selected == null) {
            status.setText("Seleccioná un backup para restaurar.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restaurar backup");
        confirm.setHeaderText("¿Restaurar " + selected.nombreArchivo() + "?");
        confirm.setContentText("Antes de restaurar, SisTienda creará una copia de emergencia del estado actual. Después deberás reiniciar la aplicación.");
        confirm.showAndWait()
                .filter(ButtonType.OK::equals)
                .ifPresent(button -> ejecutar(() -> {
                    backupService.restaurar(selected.archivo());
                    refrescar();
                    status.setText("Backup restaurado. Reiniciá SisTienda para cargar los datos restaurados.");
                }));
    }

    private void refrescar() {
        var list = backupService.listar();
        backups.setItems(FXCollections.observableArrayList(list));
        if (!list.isEmpty() && backups.getValue() == null) {
            backups.setValue(list.getFirst());
        }
        if (list.isEmpty()) {
            status.setText("Todavía no hay backups guardados.");
        } else {
            BackupInfo latest = list.getFirst();
            status.setText("Último backup: " + DATE_TIME.format(latest.creadoEn()) + " · " + humanSize(latest.bytes()));
        }
    }

    private void abrirCarpeta() {
        try {
            Files.createDirectories(backupService.backupDirectory());
            if (!Desktop.isDesktopSupported()) {
                throw new RuntimeException("Tu sistema no permite abrir la carpeta automáticamente.");
            }
            Desktop.getDesktop().open(backupService.backupDirectory().toFile());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo abrir la carpeta de backups.", e);
        }
    }

    private void ejecutar(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Throwable current = e;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            status.setText(current.getMessage() == null ? "No se pudo completar la operación." : current.getMessage());
        }
    }

    private String format(BackupInfo info) {
        return DATE_TIME.format(info.creadoEn())
                + (info.automatico() ? " · Automático" : " · Manual")
                + " · " + humanSize(info.bytes());
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024d);
        }
        return String.format("%.1f MB", bytes / (1024d * 1024d));
    }
}
