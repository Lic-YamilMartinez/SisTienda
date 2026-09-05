package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.LineaCompra;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompraRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void registraCompraActualizaStockCostoEImpideDocumentoDuplicado() throws Exception {
        var factory = new SqliteConnectionFactory(tempDir.resolve("compras.db"));
        new DatabaseInitializer(factory).initialize();

        long usuarioId;
        long productoId;
        try (var connection = factory.open()) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO usuario (username, password_hash, rol, activo) VALUES ('admin', 'hash', 'DUENIO', 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); usuarioId = keys.getLong(1); }
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO producto (nombre, unidad_medida, precio_venta, costo, stock_actual, activo) VALUES ('Arroz', 'UN', 10000, 5000, 2, 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); productoId = keys.getLong(1); }
            }
        }

        var proveedorRepo = new SqliteProveedorRepository(factory);
        var proveedor = proveedorRepo.create(new py.sistienda.core.model.Proveedor(0, "Distribuidora ABC", "123", null, null, null, true));
        var compraRepo = new SqliteCompraRepository(factory);
        var usuario = new Usuario(usuarioId, "admin", "DUENIO", true);
        var producto = new Producto(productoId, "Arroz", null, null, UnidadMedida.UN, 10000, 5000, 2, true);

        long compraId = compraRepo.registrar(usuario, proveedor, "001-001-0001",
                List.of(new LineaCompra(producto, 5, 7000)), "Reposición semanal");

        assertTrue(compraId > 0);
        var detalle = compraRepo.detalle(compraId);
        assertEquals("Distribuidora ABC", detalle.proveedor().nombre());
        assertEquals(35000, detalle.total(), 0.001);
        assertEquals(1, detalle.items().size());
        assertEquals(5, detalle.items().getFirst().cantidad(), 0.001);

        try (var connection = factory.open();
             var statement = connection.prepareStatement("SELECT stock_actual, costo FROM producto WHERE id = ?")) {
            statement.setLong(1, productoId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(7, result.getDouble("stock_actual"), 0.001);
                assertEquals(7000, result.getDouble("costo"), 0.001);
            }
        }

        RuntimeException duplicate = assertThrows(RuntimeException.class, () ->
                compraRepo.registrar(usuario, proveedor, "001-001-0001",
                        List.of(new LineaCompra(producto, 3, 7500)), null));
        assertTrue(duplicate.getMessage().contains("mismo número de documento"));

        try (var connection = factory.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT stock_actual FROM producto WHERE id = " + productoId)) {
            assertTrue(result.next());
            assertEquals(7, result.getDouble(1), 0.001, "La compra duplicada no debe mover stock");
        }
    }
}
