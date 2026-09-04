package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void categoriaProductoYMovimientosFuncionanSobreSqliteReal() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("catalogo-test.db"));
        new DatabaseInitializer(factory).initialize();

        var categoriaRepository = new SqliteCategoriaRepository(factory);
        var productoRepository = new SqliteProductoRepository(factory);
        var movimientoRepository = new SqliteMovimientoStockRepository(factory);

        var categoria = categoriaRepository.create("Hogar");
        assertTrue(categoria.id() > 0);

        Producto producto = productoRepository.create(new Producto(
                0L,
                "Detergente",
                categoria.id(),
                categoria.nombre(),
                UnidadMedida.UN,
                15000,
                10000,
                0,
                true
        ));
        assertTrue(producto.id() > 0);

        movimientoRepository.register(producto.id(), TipoMovimientoStock.ENTRADA,
                "Compra", 12, "FAC-1", null);

        Producto conEntrada = productoRepository.findAllActive().stream()
                .filter(item -> item.id() == producto.id())
                .findFirst()
                .orElseThrow();
        assertEquals(12d, conEntrada.stockActual());

        movimientoRepository.register(producto.id(), TipoMovimientoStock.SALIDA,
                "Ajuste", 5, null, null);

        Producto conSalida = productoRepository.findAllActive().stream()
                .filter(item -> item.id() == producto.id())
                .findFirst()
                .orElseThrow();
        assertEquals(7d, conSalida.stockActual());

        assertThrows(ValidationException.class,
                () -> movimientoRepository.register(producto.id(), TipoMovimientoStock.SALIDA,
                        "Ajuste", 99, null, null));
    }
}
