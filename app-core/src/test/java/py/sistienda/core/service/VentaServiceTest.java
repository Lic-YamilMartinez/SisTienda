package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.model.VentaResultado;
import py.sistienda.core.repository.VentaRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VentaServiceTest {

    private final Usuario usuario = new Usuario(1L, "admin", "DUENIO", true);
    private final CajaSesion caja = new CajaSesion(
            3L, 1L, LocalDateTime.now(), null, 100000, null, EstadoCaja.ABIERTA, null
    );

    @Test
    void venderEnEfectivo_calculaTotalYVuelto() {
        FakeVentaRepository repository = new FakeVentaRepository();
        VentaService service = new VentaService(repository);
        Producto producto = producto(UnidadMedida.UN, 8000, 6000, 10);

        VentaResultado result = service.vender(
                usuario,
                caja,
                List.of(new LineaVenta(producto, 2)),
                MetodoPago.EFECTIVO,
                20000
        );

        assertEquals(16000d, result.total());
        assertEquals(4000d, result.vuelto());
        assertEquals(4000d, result.gananciaTotal());
    }

    @Test
    void vender_rechazaStockInsuficiente() {
        VentaService service = new VentaService(new FakeVentaRepository());
        Producto producto = producto(UnidadMedida.UN, 8000, 6000, 1);

        assertThrows(ValidationException.class, () -> service.vender(
                usuario,
                caja,
                List.of(new LineaVenta(producto, 2)),
                MetodoPago.EFECTIVO,
                20000
        ));
    }

    @Test
    void venderProductoPorUnidad_rechazaCantidadDecimal() {
        VentaService service = new VentaService(new FakeVentaRepository());
        Producto producto = producto(UnidadMedida.UN, 8000, 6000, 10);

        assertThrows(ValidationException.class, () -> service.vender(
                usuario,
                caja,
                List.of(new LineaVenta(producto, 1.5)),
                MetodoPago.EFECTIVO,
                20000
        ));
    }

    private Producto producto(UnidadMedida unidad, double precio, double costo, double stock) {
        return new Producto(1L, "Producto", 1L, "General", unidad, precio, costo, stock, true);
    }

    private static final class FakeVentaRepository implements VentaRepository {
        @Override
        public VentaResultado register(long cajaSesionId, long usuarioId, MetodoPago metodoPago,
                                       double recibido, double vuelto, List<LineaVenta> lineas) {
            double total = lineas.stream().mapToDouble(LineaVenta::subtotal).sum();
            double ganancia = lineas.stream().mapToDouble(LineaVenta::ganancia).sum();
            return new VentaResultado(1L, 1, total, recibido, vuelto, ganancia, metodoPago);
        }
    }
}
