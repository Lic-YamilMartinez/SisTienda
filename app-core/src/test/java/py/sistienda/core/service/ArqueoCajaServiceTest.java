package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.*;
import py.sistienda.core.repository.ArqueoCajaRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArqueoCajaServiceTest {

    @Test
    void delegaHistorialYDetalle() {
        var apertura = LocalDateTime.of(2026, 9, 5, 8, 0);
        var cierre = LocalDateTime.of(2026, 9, 5, 18, 0);
        var resumen = new ArqueoCajaResumen(7, "admin", apertura, cierre, EstadoCaja.CERRADA,
                200000, 1450000, 12, 980000, 975000d, -5000d);
        var detalle = new ArqueoCajaDetalle(7, "admin", apertura, cierre, EstadoCaja.CERRADA,
                200000, new ResumenVentasCaja(850000, 420000, 180000, 1450000),
                new ResumenMovimientosCaja(30000, 100000), 12, 980000, 975000d, -5000d,
                "Cierre", List.of());

        ArqueoCajaRepository repo = new ArqueoCajaRepository() {
            @Override public List<ArqueoCajaResumen> findRecent(int limit) {
                assertEquals(100, limit);
                return List.of(resumen);
            }
            @Override public ArqueoCajaDetalle findDetail(long cajaId) {
                assertEquals(7, cajaId);
                return detalle;
            }
        };

        var service = new ArqueoCajaService(repo);
        assertEquals(List.of(resumen), service.listarRecientes());
        assertSame(detalle, service.detalle(7));
    }

    @Test
    void rechazaIdDeCajaInvalido() {
        ArqueoCajaRepository repo = new ArqueoCajaRepository() {
            @Override public List<ArqueoCajaResumen> findRecent(int limit) { return List.of(); }
            @Override public ArqueoCajaDetalle findDetail(long cajaId) { throw new AssertionError("No debe consultar"); }
        };
        var service = new ArqueoCajaService(repo);
        assertThrows(ValidationException.class, () -> service.detalle(0));
    }
}
