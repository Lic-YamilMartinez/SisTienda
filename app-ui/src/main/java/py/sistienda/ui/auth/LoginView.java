package py.sistienda.ui.auth;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.service.AuthService;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public final class LoginView extends StackPane {

    private final AuthService authService;
    private final Consumer<Usuario> onAuthenticated;
    private final boolean setupMode;

    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirmPassword = new PasswordField();
    private final Label error = new Label();

    public LoginView(AuthService authService, Consumer<Usuario> onAuthenticated) {
        this.authService = Objects.requireNonNull(authService);
        this.onAuthenticated = Objects.requireNonNull(onAuthenticated);
        this.setupMode = authService.requiereConfiguracionInicial();

        getStyleClass().add("auth-screen");
        setPadding(new Insets(36));
        getChildren().add(buildLayout());
    }

    private HBox buildLayout() {
        VBox hero = buildHero();
        VBox card = buildCard();

        HBox layout = new HBox(42, hero, card);
        layout.setAlignment(Pos.CENTER);
        layout.setMaxWidth(1080);
        HBox.setHgrow(hero, Priority.ALWAYS);
        return layout;
    }

    private VBox buildHero() {
        Label mark = new Label("ST");
        mark.getStyleClass().add("auth-brand-mark");

        Label eyebrow = new Label(setupMode ? "PRIMERA CONFIGURACIÓN" : "BIENVENIDO DE VUELTA");
        eyebrow.getStyleClass().add("auth-eyebrow");

        Label title = new Label(setupMode
                ? "Prepará SisTienda\npara empezar a vender"
                : "Todo tu negocio,\nen un solo lugar");
        title.getStyleClass().add("auth-hero-title");

        Label subtitle = new Label(setupMode
                ? "Creá el usuario dueño. Esta cuenta tendrá acceso completo al sistema."
                : "Ingresá para administrar catálogo, stock, caja y ventas.");
        subtitle.getStyleClass().add("auth-hero-subtitle");
        subtitle.setWrapText(true);

        VBox hero = new VBox(18, mark, eyebrow, title, subtitle);
        hero.setMaxWidth(520);
        hero.setAlignment(Pos.CENTER_LEFT);
        return hero;
    }

    private VBox buildCard() {
        Label title = new Label(setupMode ? "Crear cuenta del dueño" : "Ingresar a SisTienda");
        title.getStyleClass().add("auth-card-title");

        Label subtitle = new Label(setupMode
                ? "Usá un usuario fácil de recordar y una contraseña de al menos 8 caracteres."
                : "Usá las credenciales configuradas para esta tienda.");
        subtitle.getStyleClass().add("auth-card-subtitle");
        subtitle.setWrapText(true);

        username.setPromptText("Ej.: admin");
        password.setPromptText("Contraseña");
        confirmPassword.setPromptText("Repetir contraseña");

        username.getStyleClass().add("auth-input");
        password.getStyleClass().add("auth-input");
        confirmPassword.getStyleClass().add("auth-input");

        Label usernameLabel = fieldLabel("Usuario");
        Label passwordLabel = fieldLabel("Contraseña");
        Label confirmLabel = fieldLabel("Confirmar contraseña");

        Button submit = new Button(setupMode ? "Crear cuenta y continuar" : "Ingresar");
        submit.getStyleClass().add("auth-primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(event -> submit());
        submit.setDefaultButton(true);

        error.getStyleClass().add("auth-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        VBox fields = new VBox(8, usernameLabel, username, passwordLabel, password);
        if (setupMode) {
            fields.getChildren().addAll(confirmLabel, confirmPassword);
        }

        Region spacer = new Region();
        spacer.setMinHeight(2);

        VBox card = new VBox(18, title, subtitle, spacer, fields, error, submit);
        card.getStyleClass().add("auth-card");
        card.setPadding(new Insets(30));
        card.setPrefWidth(390);
        card.setMaxWidth(390);
        return card;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("auth-label");
        return label;
    }

    private void submit() {
        error.setVisible(false);
        error.setManaged(false);
        error.setText("");

        char[] passwordValue = password.getText().toCharArray();
        char[] confirmValue = confirmPassword.getText().toCharArray();

        try {
            Usuario usuario;
            if (setupMode) {
                if (!Arrays.equals(passwordValue, confirmValue)) {
                    throw new ValidationException("Las contraseñas no coinciden.");
                }
                usuario = authService.crearDuenoInicial(username.getText(), passwordValue);
            } else {
                usuario = authService.iniciarSesion(username.getText(), passwordValue);
            }
            password.clear();
            confirmPassword.clear();
            onAuthenticated.accept(usuario);
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(rootMessage(e));
        } finally {
            Arrays.fill(passwordValue, '\0');
            Arrays.fill(confirmValue, '\0');
        }
    }

    private void showError(String message) {
        error.setText(message == null || message.isBlank() ? "No pudimos completar la operación." : message);
        error.setVisible(true);
        error.setManaged(true);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
