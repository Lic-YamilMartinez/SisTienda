package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.*;
import py.sistienda.core.repository.MovimientoCajaRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MovimientoCajaServiceTest {

    @Test
    void calculaEfectivoEsperadoConFondoVentasIngresosYEgresos() {
        MovimientoCajaRepository repo = new MovimientoCajaRepository() {
            @Override public MovimientoCaja create(long cajaSesionId, long usuarioId, TipoMovimientoCaja tipo, String categoria, String concepto, double monto, String referencia) { return null; }
            @Override public List<MovimientoCaja> findByCaja(long cajaSesionId) { return List.of(); }
            @Override public ResumenMovimientosCaja summary(long cajaSesionId) { return new ResumenMovimientosCaja(30000, 45000); }
        };
        var service = new MovimientoCajaService(repo);
        var caja = new CajaSesion(1, 1, LocalDateTime.now(), null, 200000, null, EstadoCaja.ABIERTA, null);

        ControlEfectivoCaja control = service.control(caja, 150000);

        assertEquals(200000, control.fondoInicial(), 0.001);
        assertEquals(150000, control.ventasEfectivo(), 0.001);
        assertEquals(30000, control.ingresos(), 0.001);
        assertEquals(45000, control.egresos(), 0.001);
        assertEquals(335000, control.efectivoEsperado(), 0.001);
    }

    @Test
    void rechazaMovimientoSinMontoPositivo() {
        MovimientoCajaRepository repo = new MovimientoCajaRepository() {
            @Override public MovimientoCaja create(long cajaSesionId, long usuarioId, TipoMovimientoCaja tipo, String categoria, String concepto, double monto, String referencia) { throw new AssertionError("No debe persistir"); }
            @Override public List<MovimientoCaja> findByCaja(long cajaSesionId) { return List.of(); }
            @Override public ResumenMovimientosCaja summary(long cajaSesionId) { return ResumenMovimientosCaja.vacio(); }
        };
        var service = new MovimientoCajaService(repo);
        var caja = new CajaSesion(1, 1, LocalDateTime.now(), null, 100000, null, EstadoCaja.ABIERTA, null);
        var usuario = new Usuario(1, "admin", "DUENIO", true);

        assertThrows(ValidationException.class, () -> service.registrar(
                caja, usuario, TipoMovimientoCaja.EGRESO, "Flete", "Entrega", 0, null));
    }
}
