package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CompraDetalle;
import py.sistienda.core.model.CompraResumen;
import py.sistienda.core.model.LineaCompra;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.Proveedor;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.CompraRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CompraServiceTest {

    private final Usuario usuario = new Usuario(1, "owner", "DUENIO", true);
    private final Proveedor proveedor = new Proveedor(2, "Proveedor", null, null, null, null, true);
    private final Producto producto = new Producto(3, "Producto", null, null, UnidadMedida.KG, 10000, 7000, 10, true);

    @Test
    void rechazaCantidadNoFinita() {
        CompraService service = new CompraService(new FakeRepository());
        assertThrows(ValidationException.class, () -> service.registrar(
                usuario, proveedor, null, List.of(new LineaCompra(producto, Double.NaN, 7000)), null
        ));
    }

    @Test
    void rechazaCostoNoFinito() {
        CompraService service = new CompraService(new FakeRepository());
        assertThrows(ValidationException.class, () -> service.registrar(
                usuario, proveedor, null, List.of(new LineaCompra(producto, 1, Double.POSITIVE_INFINITY)), null
        ));
    }

    @Test
    void rechazaProveedorInactivo() {
        CompraService service = new CompraService(new FakeRepository());
        Proveedor inactivo = new Proveedor(2, "Proveedor", null, null, null, null, false);
        assertThrows(ValidationException.class, () -> service.registrar(
                usuario, inactivo, null, List.of(new LineaCompra(producto, 1, 7000)), null
        ));
    }

    private static final class FakeRepository implements CompraRepository {
        @Override
        public long registrar(Usuario usuario, Proveedor proveedor, String nroDocumento,
                              List<LineaCompra> lineas, String observacion) {
            throw new AssertionError("No debe persistir");
        }

        @Override
        public List<CompraResumen> listarRecientes(int limite) {
            return List.of();
        }

        @Override
        public CompraDetalle detalle(long compraId) {
            return null;
        }
    }
}
