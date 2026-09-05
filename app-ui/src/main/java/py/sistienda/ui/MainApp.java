package py.sistienda.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.PasswordHasher;
import py.sistienda.core.service.AuthService;
import py.sistienda.core.service.CajaService;
import py.sistienda.core.service.CategoriaService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.StockService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;
import py.sistienda.data.repository.SqliteCajaRepository;
import py.sistienda.data.repository.SqliteCategoriaRepository;
import py.sistienda.data.repository.SqliteMovimientoStockRepository;
import py.sistienda.data.repository.SqliteProductoRepository;
import py.sistienda.data.repository.SqliteUsuarioRepository;
import py.sistienda.ui.auth.LoginView;
import py.sistienda.ui.caja.CajaView;
import py.sistienda.ui.catalogo.CatalogoView;

public class MainApp extends Application {

    private SqliteConnectionFactory connectionFactory;

    @Override
    public void start(Stage stage) {
        connectionFactory = new SqliteConnectionFactory();
        new DatabaseInitializer(connectionFactory).initialize();

        var authService = new AuthService(
                new SqliteUsuarioRepository(connectionFactory),
                new PasswordHasher()
        );

        var login = new LoginView(authService, usuario -> showMain(stage, usuario));
        var scene = new Scene(login, 1180, 760);
        applyStyles(scene);

        stage.setTitle("SisTienda · Acceso");
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private void showMain(Stage stage, Usuario usuario) {
        var categoriaService = new CategoriaService(new SqliteCategoriaRepository(connectionFactory));
        var productoService = new ProductoService(new SqliteProductoRepository(connectionFactory));
        var stockService = new StockService(new SqliteMovimientoStockRepository(connectionFactory));
        var cajaService = new CajaService(new SqliteCajaRepository(connectionFactory));

        var catalogo = new CatalogoView(categoriaService, productoService, stockService);
        var caja = new CajaView(cajaService, usuario);
        var root = new MainShell(catalogo, caja, usuario);
        var scene = new Scene(root, 1360, 820);
        applyStyles(scene);

        stage.setTitle("SisTienda");
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.centerOnScreen();
    }

    private void applyStyles(Scene scene) {
        addStyle(scene, "/styles/app.css");
        addStyle(scene, "/styles/auth.css");
        addStyle(scene, "/styles/caja.css");
    }

    private void addStyle(Scene scene, String path) {
        var css = MainApp.class.getResource(path);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
