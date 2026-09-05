package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Empresa;
import py.sistienda.core.repository.EmpresaRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmpresaServiceTest {

    @Test
    void guardar_normalizaYPersisteDatos() {
        FakeEmpresaRepository repository = new FakeEmpresaRepository();
        EmpresaService service = new EmpresaService(repository);

        Empresa saved = service.guardar(
                "  Tienda   Central ",
                "80012345-6",
                "Av. Principal 123",
                "0981000000",
                " Gracias   por comprar "
        );

        assertEquals("Tienda Central", saved.nombre());
        assertEquals("Gracias por comprar", saved.mensajeTicket());
        assertEquals("0981000000", repository.current.telefono());
    }

    @Test
    void guardar_rechazaNombreVacio() {
        EmpresaService service = new EmpresaService(new FakeEmpresaRepository());
        assertThrows(ValidationException.class,
                () -> service.guardar("  ", null, null, null, null));
    }

    private static final class FakeEmpresaRepository implements EmpresaRepository {
        private Empresa current = new Empresa(1, "Mi Tienda", null, null, null, null);

        @Override
        public Empresa get() {
            return current;
        }

        @Override
        public Empresa save(Empresa empresa) {
            current = empresa;
            return current;
        }
    }
}
