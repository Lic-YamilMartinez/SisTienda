package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.CajaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CajaServiceTest {

    private final Usuario usuario = new Usuario(1L, "admin", "DUENIO", true);

    @Test
    void abrirYCerrarCaja_funciona() {
        FakeCajaRepository repository = new FakeCajaRepository();
        CajaService service = new CajaService(repository);

        CajaSesion abierta = service.abrir(usuario, 250000, "Inicio");
        assertTrue(abierta.abierta());
        assertEquals(250000d, abierta.montoApertura());

        CajaSesion cerrada = service.cerrar(abierta, 400000, "Fin");
        assertEquals(EstadoCaja.CERRADA, cerrada.estado());
        assertEquals(400000d, cerrada.montoCierre());
    }

    @Test
    void abrir_rechazaSegundaCajaAbierta() {
        FakeCajaRepository repository = new FakeCajaRepository();
        CajaService service = new CajaService(repository);
        service.abrir(usuario, 0, null);

        assertThrows(ValidationException.class,
                () -> service.abrir(usuario, 1000, null));
    }

    private static final class FakeCajaRepository implements CajaRepository {
        private CajaSesion current;

        @Override
        public Optional<CajaSesion> findOpenByUser(long usuarioId) {
            return current != null && current.abierta() ? Optional.of(current) : Optional.empty();
        }

        @Override
        public CajaSesion open(long usuarioId, double montoApertura, String notas) {
            current = new CajaSesion(1L, usuarioId, LocalDateTime.now(), null,
                    montoApertura, null, EstadoCaja.ABIERTA, notas);
            return current;
        }

        @Override
        public CajaSesion close(long cajaSesionId, double montoCierre, String notas) {
            current = new CajaSesion(current.id(), current.usuarioId(), current.fechaApertura(), LocalDateTime.now(),
                    current.montoApertura(), montoCierre, EstadoCaja.CERRADA, notas);
            return current;
        }

        @Override
        public ResumenVentasCaja salesSummary(long cajaSesionId) {
            return ResumenVentasCaja.vacio();
        }
    }
}
