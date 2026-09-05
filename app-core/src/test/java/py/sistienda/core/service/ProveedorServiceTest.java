package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Proveedor;
import py.sistienda.core.repository.ProveedorRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProveedorServiceTest {

    @Test
    void validaNombreYNormalizaCamposOpcionales() {
        FakeRepository repository = new FakeRepository();
        ProveedorService service = new ProveedorService(repository);

        assertThrows(ValidationException.class, () -> service.crear("   ", null, null, null, null));

        Proveedor creado = service.crear("  Distribuidora Central  ", " 123 ", " 0981 ", "ventas@central.com", " Asunción ");
        assertEquals("Distribuidora Central", creado.nombre());
        assertEquals("123", creado.ruc());
        assertEquals("0981", creado.telefono());
        assertEquals("Asunción", creado.direccion());
    }

    @Test
    void rechazaCorreoInvalido() {
        ProveedorService service = new ProveedorService(new FakeRepository());
        assertThrows(ValidationException.class,
                () -> service.crear("Proveedor", null, null, "correo-invalido", null));
    }

    private static final class FakeRepository implements ProveedorRepository {
        private final List<Proveedor> data = new ArrayList<>();

        @Override public List<Proveedor> findAllActive() { return List.copyOf(data); }
        @Override public Proveedor create(Proveedor proveedor) {
            Proveedor saved = new Proveedor(data.size() + 1L, proveedor.nombre(), proveedor.ruc(), proveedor.telefono(), proveedor.email(), proveedor.direccion(), true);
            data.add(saved);
            return saved;
        }
        @Override public Proveedor update(Proveedor proveedor) { return proveedor; }
        @Override public void deactivate(long proveedorId) { }
    }
}
