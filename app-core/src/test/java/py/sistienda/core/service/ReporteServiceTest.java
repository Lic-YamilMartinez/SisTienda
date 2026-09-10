package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.ProductoVendidoResumen;
import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.ReporteLineaTiempo;
import py.sistienda.core.model.ReportePeriodoResumen;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaDetalleItem;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.repository.ReporteRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReporteServiceTest {

    @Test
    void delegaResumenHistorialYDetalle() {
        LocalDate fecha = LocalDate.of(2026, 9, 5);
        FakeReporteRepository repository = new FakeReporteRepository(fecha);
        ReporteService service = new ReporteService(repository);

        assertEquals(100000d, service.resumenDiario(fecha).ventas());
        assertEquals(1, service.listarVentas(fecha).size());
        assertEquals(7L, service.detalleVenta(1L).nroTicket());
    }

    @Test
    void dashboardConsolidaPeriodoYRespetaFiltroDePago() {
        LocalDate desde = LocalDate.of(2026, 8, 1);
        LocalDate hasta = LocalDate.of(2026, 8, 31);
        FakeReporteRepository repository = new FakeReporteRepository(desde);
        ReporteService service = new ReporteService(repository);

        var dashboard = service.dashboard(desde, hasta, MetodoPago.TRANSFERENCIA);

        assertEquals(150000d, dashboard.resumen().ventas(), 0.001);
        assertEquals(MetodoPago.TRANSFERENCIA, dashboard.resumen().metodoPago());
        assertEquals(1, dashboard.lineaTiempo().size());
        assertEquals(1, dashboard.productosMasVendidos().size());
        assertEquals(1, dashboard.ventas().size());
    }

    @Test
    void dashboardRechazaRangoInvertido() {
        ReporteService service = new ReporteService(new FakeReporteRepository(LocalDate.now()));
        assertThrows(ValidationException.class, () -> service.dashboard(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), null));
    }

    @Test
    void detalleVenta_rechazaIdInvalidoONoExistente() {
        ReporteService service = new ReporteService(new FakeReporteRepository(LocalDate.now()));

        assertThrows(IllegalArgumentException.class, () -> service.detalleVenta(0));
        assertThrows(IllegalArgumentException.class, () -> service.detalleVenta(999));
    }

    private static final class FakeReporteRepository implements ReporteRepository {
        private final LocalDate fecha;

        private FakeReporteRepository(LocalDate fecha) {
            this.fecha = fecha;
        }

        @Override
        public ReporteDiario resumenDiario(LocalDate fecha) {
            return new ReporteDiario(fecha, 100000, 25000, 1, 100000, 100000, 0, 0);
        }

        @Override
        public List<VentaResumen> listarVentas(LocalDate fecha) {
            return listarVentas(fecha, fecha, null, 1000);
        }

        @Override
        public ReportePeriodoResumen resumenPeriodo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
            return new ReportePeriodoResumen(
                    desde, hasta, metodoPago,
                    150000, 100000, 50000, 2, 75000,
                    100000, 150000, 0,
                    10000, 5000, 55000
            );
        }

        @Override
        public List<ReporteLineaTiempo> lineaTiempo(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
            return List.of(new ReporteLineaTiempo(desde, 150000, 50000, 2));
        }

        @Override
        public List<ProductoVendidoResumen> productosMasVendidos(LocalDate desde, LocalDate hasta, MetodoPago metodoPago, int limite) {
            return List.of(new ProductoVendidoResumen(1, "Producto", UnidadMedida.UN, 2, 150000, 100000, 50000));
        }

        @Override
        public List<VentaResumen> listarVentas(LocalDate desde, LocalDate hasta, MetodoPago metodoPago, int limite) {
            return List.of(new VentaResumen(
                    1L, 7L, LocalDateTime.of(this.fecha, java.time.LocalTime.NOON),
                    "admin", metodoPago == null ? MetodoPago.EFECTIVO : metodoPago, 100000, 25000, false
            ));
        }

        @Override
        public Optional<VentaDetalle> detalleVenta(long ventaId) {
            if (ventaId != 1L) {
                return Optional.empty();
            }
            return Optional.of(new VentaDetalle(
                    1L, 7L, LocalDateTime.of(fecha, java.time.LocalTime.NOON),
                    "admin", MetodoPago.EFECTIVO, 100000, 120000, 20000, 25000, false,
                    List.of(new VentaDetalleItem("Producto", 1, 100000, 100000))
            ));
        }
    }
}
