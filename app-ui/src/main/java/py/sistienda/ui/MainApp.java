package py.sistienda.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.PasswordHasher;
import py.sistienda.core.service.ArqueoCajaService;
import py.sistienda.core.service.AuthService;
import py.sistienda.core.service.BackupService;
import py.sistienda.core.service.CajaService;
import py.sistienda.core.service.CategoriaService;
import py.sistienda.core.service.CodigoBarrasService;
import py.sistienda.core.service.CompraService;
import py.sistienda.core.service.ConfiguracionPosService;
import py.sistienda.core.service.EmpresaService;
import py.sistienda.core.service.MovimientoCajaService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.ProveedorService;
import py.sistienda.core.service.ReporteService;
import py.sistienda.core.service.StockService;
import py.sistienda.core.service.VentaService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;
import py.sistienda.data.repository.SqliteArqueoCajaRepository;
import py.sistienda.data.repository.SqliteBackupRepository;
import py.sistienda.data.repository.SqliteCajaRepository;
import py.sistienda.data.repository.SqliteCategoriaRepository;
import py.sistienda.data.repository.SqliteCompraRepository;
import py.sistienda.data.repository.SqliteConfiguracionPosRepository;
import py.sistienda.data.repository.SqliteEmpresaRepository;
import py.sistienda.data.repository.SqliteMovimientoCajaRepository;
import py.sistienda.data.repository.SqliteMovimientoStockRepository;
import py.sistienda.data.repository.SqliteProductoRepository;
import py.sistienda.data.repository.SqliteProveedorRepository;
import py.sistienda.data.repository.SqliteReporteRepository;
import py.sistienda.data.repository.SqliteUsuarioRepository;
import py.sistienda.data.repository.SqliteVentaRepository;
import py.sistienda.ui.auth.LoginView;
import py.sistienda.ui.caja.CajaView;
import py.sistienda.ui.catalogo.CatalogoView;
import py.sistienda.ui.compras.ComprasView;
import py.sistienda.ui.configuracion.ConfiguracionView;
import py.sistienda.ui.reportes.ReportesView;

public class MainApp extends Application {

    private SqliteConnectionFactory connectionFactory;
    private BackupService backupService;

    @Override
    public void start(Stage stage) {
        connectionFactory = new SqliteConnectionFactory();
        new DatabaseInitializer(connectionFactory).initialize();

        backupService = new BackupService(new SqliteBackupRepository(connectionFactory));
        try {
            backupService.crearAutomaticoSiHaceFalta();
        } catch (RuntimeException e) {
            System.err.println("SisTienda no pudo crear el backup automático: " + e.getMessage());
        }

        var authService = new AuthService(new SqliteUsuarioRepository(connectionFactory), new PasswordHasher());
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
        var codigoBarrasService = new CodigoBarrasService();
        var configuracionPosService = new ConfiguracionPosService(new SqliteConfiguracionPosRepository(connectionFactory));
        var categoriaService = new CategoriaService(new SqliteCategoriaRepository(connectionFactory));
        var productoService = new ProductoService(new SqliteProductoRepository(connectionFactory), codigoBarrasService);
        var stockService = new StockService(new SqliteMovimientoStockRepository(connectionFactory));
        var cajaService = new CajaService(new SqliteCajaRepository(connectionFactory));
        var movimientoCajaService = new MovimientoCajaService(new SqliteMovimientoCajaRepository(connectionFactory));
        var arqueoCajaService = new ArqueoCajaService(new SqliteArqueoCajaRepository(connectionFactory));
        var ventaService = new VentaService(new SqliteVentaRepository(connectionFactory));
        var reporteService = new ReporteService(new SqliteReporteRepository(connectionFactory));
        var empresaService = new EmpresaService(new SqliteEmpresaRepository(connectionFactory));
        var proveedorService = new ProveedorService(new SqliteProveedorRepository(connectionFactory));
        var compraService = new CompraService(new SqliteCompraRepository(connectionFactory));

        var root = new MainShell(
                () -> new CatalogoView(categoriaService, productoService, stockService,
                        configuracionPosService, codigoBarrasService),
                () -> new CajaView(cajaService, movimientoCajaService, arqueoCajaService, productoService, ventaService,
                        reporteService, empresaService, configuracionPosService, codigoBarrasService, usuario),
                () -> new ReportesView(reporteService, empresaService, configuracionPosService),
                () -> new ComprasView(proveedorService, productoService, compraService, usuario),
                () -> new ConfiguracionView(empresaService, backupService, configuracionPosService),
                usuario
        );
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
        addStyle(scene, "/styles/venta.css");
        addStyle(scene, "/styles/reportes.css");
        addStyle(scene, "/styles/compras.css");
        addStyle(scene, "/styles/configuracion.css");
        addStyle(scene, "/styles/ticket.css");
    }

    private void addStyle(Scene scene, String path) {
        var css = MainApp.class.getResource(path);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
