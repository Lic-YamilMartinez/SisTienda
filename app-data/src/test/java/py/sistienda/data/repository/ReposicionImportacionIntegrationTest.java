package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionProductoEntrada;
import py.sistienda.core.model.ImportacionProductoFila;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.service.CodigoBarrasService;
import py.sistienda.core.service.ImportacionProductoService;
import py.sistienda.core.service.ProductoService;
import py.sistienda.core.service.ReposicionService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReposicionImportacionIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void importaCatalogoConStockInicialYGeneraReposicion() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("reposicion.db"));
        new DatabaseInitializer(factory).initialize();
        seedOwner(factory);

        Usuario owner = new Usuario(1, "owner", "DUENIO", true);
        var service = new ImportacionProductoService(
                new SqliteImportacionProductoRepository(factory), new AutorizacionService());

        var rows = List.of(
                new ImportacionProductoFila(2, "Yerba 1kg", "Alimentos", "UN",
                        "8000", "10000", "5", "2", "10", "7790001", ""),
                new ImportacionProductoFila(3, "Carne molida", "Carnes", "KG",
                        "35000", "45000", "2,5", "1", "5", "", "")
        );
        var validation = service.validar(owner, rows);
        assertTrue(validation.stream().allMatch(item -> item.valida()));

        var result = service.importar(owner, validation);
        assertEquals(2, result.productosCreados());
        assertEquals(1, result.categoriasCreadas());
        assertEquals(2, result.movimientosStock());

        ProductoService productoService = new ProductoService(new SqliteProductoRepository(factory), new CodigoBarrasService());
        var products = productoService.listarActivos();
        var yerba = products.stream().filter(p -> p.nombre().equals("Yerba 1kg")).findFirst().orElseThrow();
        var carne = products.stream().filter(p -> p.nombre().equals("Carne molida")).findFirst().orElseThrow();
        assertEquals(5d, yerba.stockActual(), 0.001d);
        assertEquals(2d, yerba.stockMinimo(), 0.001d);
        assertEquals(10d, yerba.stockIdeal(), 0.001d);
        assertEquals("7790001", yerba.codigoBarras());
        assertEquals(2.5d, carne.stockActual(), 0.001d);
        assertNotNull(carne.pluBalanza());

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            var movement = statement.executeQuery("""
                    SELECT COUNT(*) AS total, MIN(usuario_id) AS usuario
                    FROM mov_stock
                    WHERE motivo = 'Stock inicial - importación'
                    """);
            assertTrue(movement.next());
            assertEquals(2, movement.getInt("total"));
            assertEquals(1, movement.getInt("usuario"));
            statement.execute("UPDATE producto SET stock_actual = 1 WHERE id = " + yerba.id());
        }

        var reposicion = new ReposicionService(productoService).listarPendientes();
        var pendiente = reposicion.stream().filter(item -> item.producto().id() == yerba.id()).findFirst().orElseThrow();
        assertEquals(9d, pendiente.cantidadSugerida(), 0.001d);
        assertEquals(72_000d, pendiente.inversionEstimada(), 0.001d);

        var duplicate = service.validar(owner, List.of(
                new ImportacionProductoFila(2, "Otro producto", "Alimentos", "UN",
                        "100", "200", "0", "0", "0", "7790001", "")
        ));
        assertFalse(duplicate.getFirst().valida());
        assertTrue(duplicate.getFirst().resumenErrores().contains("ya existe"));

        Usuario cashier = new Usuario(2, "cashier", "CAJERO", true);
        assertThrows(ValidationException.class, () -> service.validar(cashier, rows));
    }

    @Test
    void importacionEsAtomicaAnteDuplicadoDeUltimoMomento() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("atomic.db"));
        new DatabaseInitializer(factory).initialize();
        seedOwner(factory);
        var repository = new SqliteImportacionProductoRepository(factory);

        List<ImportacionProductoEntrada> entries = List.of(
                new ImportacionProductoEntrada(2, "Producto A", "Nueva", UnidadMedida.UN,
                        100, 200, 2, 0, 0, "ABC123", null),
                new ImportacionProductoEntrada(3, "Producto B", "Nueva", UnidadMedida.UN,
                        100, 200, 2, 0, 0, "ABC123", null)
        );
        assertThrows(RuntimeException.class, () -> repository.importar(entries, 1));

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            var products = statement.executeQuery("SELECT COUNT(*) FROM producto WHERE nombre IN ('Producto A','Producto B')");
            assertTrue(products.next());
            assertEquals(0, products.getInt(1));
            var category = statement.executeQuery("SELECT COUNT(*) FROM categoria_producto WHERE nombre = 'Nueva'");
            assertTrue(category.next());
            assertEquals(0, category.getInt(1));
        }
    }

    private void seedOwner(SqliteConnectionFactory factory) throws Exception {
        try (var connection = factory.open(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO usuario (id, username, password_hash, rol, activo)
                    VALUES (1, 'owner', 'hash', 'DUENIO', 1)
                    """);
        }
    }
}
