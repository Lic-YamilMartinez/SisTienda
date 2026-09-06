package py.sistienda.ui.usuarios;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.UsuarioService;

import java.util.Arrays;

public final class UsuariosView extends BorderPane {

    private final UsuarioService usuarioService;
    private final Usuario actor;
    private final ObservableList<Usuario> usuarios = FXCollections.observableArrayList();
    private final TableView<Usuario> tabla = new TableView<>();
    private final Label totalActivos = new Label("0");
    private final Label totalCajeros = new Label("0");
    private final Label totalVendedores = new Label("0");
    private final Label feedback = new Label();

    public UsuariosView(UsuarioService usuarioService, Usuario actor) {
        this.usuarioService = usuarioService;
        this.actor = actor;
        getStyleClass().add("content-area");
        setPadding(new Insets(24, 30, 26, 30));
        setTop(buildHeader());
        setCenter(buildContent());
        configurarTabla();
        recargar();
    }

    private VBox buildHeader() {
        Label eyebrow = new Label("SEGURIDAD");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Usuarios & Roles");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Definí quién puede vender, mover caja, ver costos, administrar stock y acceder a información sensible.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);

        Button nuevo = new Button("+ Nuevo usuario");
        nuevo.getStyleClass().add("primary-button");
        nuevo.setOnAction(event -> crearUsuario());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(14, new VBox(2, eyebrow, title, subtitle), spacer, nuevo);
        row.setAlignment(Pos.CENTER_LEFT);

        feedback.getStyleClass().add("feedback-label");
        feedback.setVisible(false);
        feedback.setManaged(false);

        HBox metrics = new HBox(12,
                metric("Usuarios activos", totalActivos, "Con acceso al sistema"),
                metric("Cajeros", totalCajeros, "Operación de caja"),
                metric("Vendedores", totalVendedores, "Venta y consulta")
        );
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        return new VBox(16, row, feedback, metrics);
    }

    private VBox metric(String title, Label value, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        value.getStyleClass().add("metric-value");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("metric-hint");
        VBox box = new VBox(5, titleLabel, value, hintLabel);
        box.getStyleClass().add("metric-card");
        box.setPadding(new Insets(14, 17, 14, 17));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox buildContent() {
        Label title = new Label("Accesos de la tienda");
        title.getStyleClass().add("report-section-title");
        Label hint = new Label("Los usuarios desactivados conservan su historial, pero ya no pueden iniciar sesión.");
        hint.getStyleClass().add("report-section-hint");
        HBox header = new HBox(8, title, hint);
        header.setAlignment(Pos.BASELINE_LEFT);
        header.setPadding(new Insets(10, 12, 8, 12));

        VBox card = new VBox(0, header, tabla);
        card.getStyleClass().add("table-panel");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox content = new VBox(card);
        content.setPadding(new Insets(16, 0, 0, 0));
        VBox.setVgrow(card, Priority.ALWAYS);
        return content;
    }

    private void configurarTabla() {
        tabla.setItems(usuarios);
        tabla.setPlaceholder(new Label("No hay usuarios para mostrar."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getStyleClass().add("catalog-table");

        TableColumn<Usuario, String> username = new TableColumn<>("Usuario");
        username.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().username()));
        username.setPrefWidth(220);

        TableColumn<Usuario, String> rol = new TableColumn<>("Rol");
        rol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().rolUsuario().descripcion()));
        rol.setPrefWidth(170);

        TableColumn<Usuario, Usuario> estado = new TableColumn<>("Estado");
        estado.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        estado.setPrefWidth(120);
        estado.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Usuario item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                badge.getStyleClass().removeAll("status-ok", "status-empty");
                badge.setText(item.activo() ? "Activo" : "Inactivo");
                badge.getStyleClass().add(item.activo() ? "status-ok" : "status-empty");
                setGraphic(badge);
            }
        });

        TableColumn<Usuario, Usuario> acciones = new TableColumn<>("Acciones");
        acciones.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        acciones.setPrefWidth(330);
        acciones.setCellFactory(column -> new TableCell<>() {
            private final Button role = actionButton("Cambiar rol");
            private final Button password = actionButton("Contraseña");
            private final Button state = actionButton("Desactivar");
            private final HBox box = new HBox(7, role, password, state);
            {
                state.getStyleClass().add("danger-link-button");
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(Usuario item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                role.setDisable(item.id() == actor.id());
                role.setOnAction(event -> cambiarRol(item));
                password.setOnAction(event -> restablecerPassword(item));
                state.setText(item.activo() ? "Desactivar" : "Activar");
                state.setDisable(item.id() == actor.id());
                state.setOnAction(event -> cambiarEstado(item));
                setGraphic(box);
            }
        });

        tabla.getColumns().setAll(username, rol, estado, acciones);
    }

    private Button actionButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("table-action-button");
        return button;
    }

    private void crearUsuario() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Nuevo usuario");
        dialog.setHeaderText("Crear acceso a SisTienda");
        ButtonType guardar = new ButtonType("Crear usuario", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        TextField username = new TextField();
        username.setPromptText("Ej.: caja01");
        ComboBox<RolUsuario> rol = new ComboBox<>(FXCollections.observableArrayList(RolUsuario.values()));
        rol.setValue(RolUsuario.CAJERO);
        rol.setMaxWidth(Double.MAX_VALUE);
        PasswordField password = new PasswordField();
        password.setPromptText("Mínimo 8 caracteres");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repetir contraseña");
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);

        for (Control control : new Control[]{username, rol, password, confirm}) {
            control.getStyleClass().add("form-control");
            control.setMaxWidth(Double.MAX_VALUE);
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        addField(grid, 0, "Usuario", username);
        addField(grid, 1, "Rol", rol);
        addField(grid, 2, "Contraseña", password);
        addField(grid, 3, "Confirmar contraseña", confirm);
        VBox content = new VBox(8, grid, error);
        content.setPadding(new Insets(6));
        content.setPrefWidth(470);
        dialog.getDialogPane().setContent(content);
        applyStyle(dialog);

        Node saveButton = dialog.getDialogPane().lookupButton(guardar);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (!password.getText().equals(confirm.getText())) {
                error.setText("Las contraseñas no coinciden.");
                event.consume();
                return;
            }
            char[] secret = password.getText().toCharArray();
            try {
                usuarioService.crear(actor, username.getText(), secret, rol.getValue());
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            } finally {
                Arrays.fill(secret, '\0');
            }
        });

        dialog.showAndWait().filter(guardar::equals).ifPresent(result -> {
            mostrarFeedback("Usuario creado correctamente.");
            recargar();
        });
    }

    private void cambiarRol(Usuario usuario) {
        ChoiceDialog<RolUsuario> dialog = new ChoiceDialog<>(usuario.rolUsuario(), RolUsuario.values());
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Cambiar rol");
        dialog.setHeaderText(usuario.username());
        dialog.setContentText("Nuevo rol:");
        applyStyle(dialog);
        dialog.showAndWait().ifPresent(rol -> ejecutar(() -> {
            usuarioService.cambiarRol(actor, usuario.id(), rol);
            mostrarFeedback("Rol actualizado para " + usuario.username() + ".");
            recargar();
        }));
    }

    private void restablecerPassword(Usuario usuario) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Restablecer contraseña");
        dialog.setHeaderText("Nueva contraseña para " + usuario.username());
        ButtonType guardar = new ButtonType("Guardar contraseña", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        PasswordField password = new PasswordField();
        password.setPromptText("Mínimo 8 caracteres");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Repetir contraseña");
        Label error = new Label();
        error.getStyleClass().add("form-error");
        VBox content = new VBox(8,
                field("Nueva contraseña", password),
                field("Confirmar contraseña", confirm),
                error
        );
        content.setPrefWidth(440);
        dialog.getDialogPane().setContent(content);
        applyStyle(dialog);

        Node save = dialog.getDialogPane().lookupButton(guardar);
        save.addEventFilter(ActionEvent.ACTION, event -> {
            if (!password.getText().equals(confirm.getText())) {
                error.setText("Las contraseñas no coinciden.");
                event.consume();
                return;
            }
            char[] secret = password.getText().toCharArray();
            try {
                usuarioService.restablecerPassword(actor, usuario.id(), secret);
            } catch (RuntimeException e) {
                error.setText(rootMessage(e));
                event.consume();
            } finally {
                Arrays.fill(secret, '\0');
            }
        });

        dialog.showAndWait().filter(guardar::equals).ifPresent(result ->
                mostrarFeedback("Contraseña actualizada para " + usuario.username() + "."));
    }

    private void cambiarEstado(Usuario usuario) {
        boolean activar = !usuario.activo();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(getScene().getWindow());
        confirmation.setTitle(activar ? "Activar usuario" : "Desactivar usuario");
        confirmation.setHeaderText((activar ? "¿Activar " : "¿Desactivar ") + usuario.username() + "?");
        confirmation.setContentText(activar
                ? "Podrá volver a iniciar sesión con su contraseña actual."
                : "No podrá iniciar sesión, pero su historial permanecerá intacto.");
        applyStyle(confirmation);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            ejecutar(() -> {
                usuarioService.cambiarEstado(actor, usuario.id(), activar);
                mostrarFeedback(activar ? "Usuario activado." : "Usuario desactivado.");
                recargar();
            });
        }
    }

    private void recargar() {
        ejecutar(() -> {
            usuarios.setAll(usuarioService.listar(actor));
            totalActivos.setText(Long.toString(usuarios.stream().filter(Usuario::activo).count()));
            totalCajeros.setText(Long.toString(usuarios.stream().filter(Usuario::activo)
                    .filter(user -> user.rolUsuario() == RolUsuario.CAJERO).count()));
            totalVendedores.setText(Long.toString(usuarios.stream().filter(Usuario::activo)
                    .filter(user -> user.rolUsuario() == RolUsuario.VENDEDOR).count()));
            tabla.refresh();
        });
    }

    private void addField(GridPane grid, int row, String labelText, Control control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        grid.add(label, 0, row * 2);
        grid.add(control, 0, row * 2 + 1);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private VBox field(String labelText, Control control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        control.getStyleClass().add("form-control");
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
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

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause().getMessage() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "No pudimos completar la operación." : current.getMessage();
    }

    private void applyStyle(Dialog<?> dialog) {
        var css = UsuariosView.class.getResource("/styles/app.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
    }
}
