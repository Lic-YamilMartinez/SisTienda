package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.ProductoRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoServiceTest {

    @Test
    void crear_normalizaNombreYDelegaAlRepositorio() {
        FakeProductoRepository repository = new FakeProductoRepository();
        ProductoService service = new ProductoService(repository);

        Producto creado = service.crear("  Arroz   1kg ", 1L, "Alimentos", UnidadMedida.UN, 12000, 9000);

        assertEquals("Arroz 1kg", creado.nombre());
        assertEquals(1L, creado.id());
        assertEquals(0d, creado.stockActual());
    }

    @Test
    void crear_rechazaMontosNegativos() {
        ProductoService service = new ProductoService(new FakeProductoRepository());

        assertThrows(ValidationException.class,
                () -> service.crear("Producto", null, null, UnidadMedida.UN, -1, 0));
    }

    @Test
    void actualizar_preservaStockActual() {
        FakeProductoRepository repository = new FakeProductoRepository();
        ProductoService service = new ProductoService(repository);
        Producto actual = new Producto(7L, "Anterior", 1L, "Alimentos", UnidadMedida.UN, 1000, 600, 25, true);

        Producto actualizado = service.actualizar(actual, "Nuevo", 2L, "Bebidas", UnidadMedida.UN, 1500, 800);

        assertEquals(25d, actualizado.stockActual());
        assertEquals(7L, actualizado.id());
    }

    @Test
    void actualizar_noPermiteCambiarUnidadConStock() {
        ProductoService service = new ProductoService(new FakeProductoRepository());
        Producto actual = new Producto(7L, "Harina", 1L, "Alimentos", UnidadMedida.KG, 1000, 600, 2.5, true);

        assertThrows(ValidationException.class,
                () -> service.actualizar(actual, "Harina", 1L, "Alimentos", UnidadMedida.UN, 1000, 600));
    }

    @Test
    void desactivar_rechazaProductoConStock() {
        ProductoService service = new ProductoService(new FakeProductoRepository());
        Producto actual = new Producto(7L, "Producto", null, null, UnidadMedida.UN, 1000, 600, 1, true);

        assertThrows(ValidationException.class, () -> service.desactivar(actual));
    }

    @Test
    void desactivar_productoSinStockDelegaAlRepositorio() {
        FakeProductoRepository repository = new FakeProductoRepository();
        ProductoService service = new ProductoService(repository);
        Producto actual = new Producto(7L, "Producto", null, null, UnidadMedida.UN, 1000, 600, 0, true);

        service.desactivar(actual);

        assertTrue(repository.deactivated);
    }

    private static final class FakeProductoRepository implements ProductoRepository {
        private final List<Producto> productos = new ArrayList<>();
        private boolean deactivated;

        @Override
        public List<Producto> findAllActive() {
            return List.copyOf(productos);
        }

        @Override
        public Producto create(Producto producto) {
            Producto persisted = new Producto(1L, producto.nombre(), producto.categoriaId(), producto.categoriaNombre(),
                    producto.unidadMedida(), producto.precioVenta(), producto.costo(), producto.stockActual(), true);
            productos.add(persisted);
            return persisted;
        }

        @Override
        public Producto update(Producto producto) {
            return producto;
        }

        @Override
        public void deactivate(long productoId) {
            deactivated = true;
        }
    }
}
