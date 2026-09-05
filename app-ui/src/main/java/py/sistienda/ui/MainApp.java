package py.sistienda.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import py.sistienda.core.service.CategoriaService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.StockService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;
import py.sistienda.data.repository.SqliteCategoriaRepository;
import py.sistienda.data.repository.SqliteMovimientoStockRepository;
import py.sistienda.data.repository.SqliteProductoRepository;
import py.sistienda.ui.catalogo.CatalogoView;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        var connectionFactory = new SqliteConnectionFactory();
        var databaseInitializer = new DatabaseInitializer(connectionFactory);
        databaseInitializer.initialize();

        var categoriaService = new CategoriaService(new SqliteCategoriaRepository(connectionFactory));
        var productoService = new ProductoService(new SqliteProductoRepository(connectionFactory));
        var stockService = new StockService(new SqliteMovimientoStockRepository(connectionFactory));

        var catalogo = new CatalogoView(categoriaService, productoService, stockService);
        var root = new MainShell(catalogo);
        var scene = new Scene(root, 1360, 820);

        var css = MainApp.class.getResource("/styles/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("SisTienda · Catálogo & Stock");
        stage.setMinWidth(1080);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
