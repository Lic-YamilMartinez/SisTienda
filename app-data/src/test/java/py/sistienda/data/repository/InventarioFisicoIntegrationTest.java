package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.InventarioConteoItem;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.service.InventarioService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventarioFisicoIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void conteoAjustaStockYDejaAuditoriaAtomica() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("inventario.db"));
        new DatabaseInitializer(factory).initialize();
        seed(factory);

        Usuario owner = new Usuario(1, "owner", "DUENIO", true);
        InventarioService service = new InventarioService(new SqliteInventarioRepository(factory), new AutorizacionService());

        var resultado = service.registrar(owner, "Conteo semanal", "Cierre del sábado", List.of(
                new InventarioConteoItem(1, "Galletita", UnidadMedida.UN, 10d, 7d),
                new InventarioConteoItem(2, "Queso", UnidadMedida.KG, 2.5d, 3.1d),
                new InventarioConteoItem(3, "Agua", UnidadMedida.UN, 5d, 5d)
        ));

        assertEquals(3, resultado.productosContados());
        assertEquals(2, resultado.productosAjustados());

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            try (var stock = statement.executeQuery("SELECT id, stock_actual FROM producto ORDER BY id")) {
                assertTrue(stock.next());
                assertEquals(7d, stock.getDouble("stock_actual"), 0.0001d);
                assertTrue(stock.next());
                assertEquals(3.1d, stock.getDouble("stock_actual"), 0.0001d);
                assertTrue(stock.next());
                assertEquals(5d, stock.getDouble("stock_actual"), 0.0001d);
            }

            try (var movements = statement.executeQuery("""
                    SELECT tipo, cantidad, usuario_id, referencia, motivo
                    FROM mov_stock
                    WHERE motivo = 'AJUSTE INVENTARIO'
                    ORDER BY producto_id
                    """)) {
                assertTrue(movements.next());
                assertEquals("SALIDA", movements.getString("tipo"));
                assertEquals(3d, movements.getDouble("cantidad"), 0.0001d);
                assertEquals(1L, movements.getLong("usuario_id"));
                assertEquals("Inventario #" + resultado.id(), movements.getString("referencia"));

                assertTrue(movements.next());
                assertEquals("ENTRADA", movements.getString("tipo"));
                assertEquals(0.6d, movements.getDouble("cantidad"), 0.0001d);
            }
        }

        var historial = service.recientes(owner, 10);
        assertEquals(1, historial.size());
        assertEquals(3, historial.getFirst().productosContados());
        assertEquals(2, historial.getFirst().productosAjustados());
        assertEquals(3, service.detalle(owner, resultado.id()).size());
    }

    @Test
    void rechazaConteoDesactualizadoSinAplicarAjustes() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("stale.db"));
        new DatabaseInitializer(factory).initialize();
        seed(factory);
        Usuario owner = new Usuario(1, "owner", "DUENIO", true);
        InventarioService service = new InventarioService(new SqliteInventarioRepository(factory), new AutorizacionService());

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            statement.execute("UPDATE producto SET stock_actual = 9 WHERE id = 1");
        }

        assertThrows(ValidationException.class, () -> service.registrar(owner, "Conteo", null, List.of(
                new InventarioConteoItem(1, "Galletita", UnidadMedida.UN, 10d, 8d)
        )));

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            try (var count = statement.executeQuery("SELECT COUNT(*) FROM inventario_conteo")) {
                assertTrue(count.next());
                assertEquals(0, count.getInt(1));
            }
            try (var stock = statement.executeQuery("SELECT stock_actual FROM producto WHERE id = 1")) {
                assertTrue(stock.next());
                assertEquals(9d, stock.getDouble(1), 0.0001d);
            }
        }
    }

    @Test
    void cajeroNoPuedeAjustarInventario() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("permission.db"));
        new DatabaseInitializer(factory).initialize();
        seed(factory);
        Usuario cashier = new Usuario(2, "cashier", "CAJERO", true);
        InventarioService service = new InventarioService(new SqliteInventarioRepository(factory), new AutorizacionService());

        assertThrows(ValidationException.class, () -> service.registrar(cashier, "Conteo", null, List.of(
                new InventarioConteoItem(1, "Galletita", UnidadMedida.UN, 10d, 10d)
        )));
    }

    private void seed(SqliteConnectionFactory factory) throws Exception {
        try (var connection = factory.open(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO usuario (id, username, password_hash, rol, activo) VALUES
                    (1, 'owner', 'hash', 'DUENIO', 1),
                    (2, 'cashier', 'hash', 'CAJERO', 1)
                    """);
            statement.execute("""
                    INSERT INTO producto
                        (id, nombre, unidad_medida, precio_venta, costo, stock_actual, activo)
                    VALUES
                        (1, 'Galletita', 'UN', 10000, 7000, 10, 1),
                        (2, 'Queso', 'KG', 45000, 30000, 2.5, 1),
                        (3, 'Agua', 'UN', 5000, 3000, 5, 1)
                    """);
        }
    }
}
