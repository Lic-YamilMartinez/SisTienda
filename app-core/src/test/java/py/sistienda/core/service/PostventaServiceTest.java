package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.DevolucionResultado;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaPostventa;
import py.sistienda.core.model.VentaPostventaLinea;
import py.sistienda.core.repository.PostventaRepository;
import py.sistienda.core.security.AutorizacionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostventaServiceTest {

    @Test
    void duenoPuedeRegistrarDevolucionValida() {
        FakePostventaRepository repository = new FakePostventaRepository(venta(false, 0));
        PostventaService service = new PostventaService(repository, new AutorizacionService());
        Usuario owner = new Usuario(1, "owner", "DUENIO", true);
        CajaSesion caja = cajaAbierta(owner.id());

        DevolucionResultado result = service.devolver(
                owner, caja, 10, "Producto defectuoso",
                List.of(new DevolucionLineaSolicitud(100, 1))
        );

        assertTrue(repository.devolverInvocado);
        assertEquals(25000d, result.totalDevuelto(), 0.001);
    }

    @Test
    void cajeroNoPuedeGestionarPostventa() {
        FakePostventaRepository repository = new FakePostventaRepository(venta(false, 0));
        PostventaService service = new PostventaService(repository, new AutorizacionService());
        Usuario cajero = new Usuario(2, "cajero", "CAJERO", true);

        assertThrows(ValidationException.class, () -> service.obtenerVenta(cajero, 10));
    }

    @Test
    void noPermiteDevolverMasQueLaCantidadDisponible() {
        FakePostventaRepository repository = new FakePostventaRepository(venta(false, 1));
        PostventaService service = new PostventaService(repository, new AutorizacionService());
        Usuario owner = new Usuario(1, "owner", "DUENIO", true);

        assertThrows(ValidationException.class, () -> service.devolver(
                owner, cajaAbierta(owner.id()), 10, "Cambio",
                List.of(new DevolucionLineaSolicitud(100, 2))
        ));
    }

    @Test
    void ventaConDevolucionesNoPuedeAnularseCompleta() {
        FakePostventaRepository repository = new FakePostventaRepository(venta(false, 25000));
        PostventaService service = new PostventaService(repository, new AutorizacionService());
        Usuario owner = new Usuario(1, "owner", "DUENIO", true);

        assertThrows(ValidationException.class, () -> service.anular(owner, 10, "Error de carga"));
    }

    private static VentaPostventa venta(boolean anulada, double totalDevuelto) {
        return new VentaPostventa(
                10,
                77,
                5,
                LocalDateTime.of(2026, 9, 11, 18, 30),
                MetodoPago.EFECTIVO,
                50000,
                20000,
                anulada,
                anulada ? "Error" : null,
                totalDevuelto,
                List.of(new VentaPostventaLinea(
                        100,
                        20,
                        "Producto",
                        UnidadMedida.UN,
                        2,
                        totalDevuelto > 0 ? 1 : 0,
                        25000,
                        15000
                ))
        );
    }

    private static CajaSesion cajaAbierta(long usuarioId) {
        return new CajaSesion(
                50,
                usuarioId,
                LocalDateTime.now(),
                null,
                100000,
                null,
                EstadoCaja.ABIERTA,
                null
        );
    }

    private static final class FakePostventaRepository implements PostventaRepository {
        private final VentaPostventa venta;
        private boolean devolverInvocado;

        private FakePostventaRepository(VentaPostventa venta) {
            this.venta = venta;
        }

        @Override
        public Optional<VentaPostventa> findVenta(long ventaId) {
            return ventaId == venta.ventaId() ? Optional.of(venta) : Optional.empty();
        }

        @Override
        public void anular(long ventaId, long usuarioId, String motivo) {
        }

        @Override
        public DevolucionResultado devolver(long ventaId, long cajaSesionId, long usuarioId, String motivo,
                                             List<DevolucionLineaSolicitud> lineas) {
            devolverInvocado = true;
            return new DevolucionResultado(1, ventaId, venta.nroTicket(), 25000, 15000, 10000);
        }
    }
}
