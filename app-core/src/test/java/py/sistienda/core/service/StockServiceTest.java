package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.repository.MovimientoStockRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockServiceTest {

    @Test
    void registrar_unidadesRechazaCantidadDecimal() {
        StockService service = new StockService(new CapturingRepository());
        Producto producto = producto(UnidadMedida.UN, 10);

        assertThrows(ValidationException.class,
                () -> service.registrar(producto, TipoMovimientoStock.ENTRADA, "Compra", 1.5, null, null));
    }

    @Test
    void registrar_salidaRechazaCantidadMayorAlStock() {
        StockService service = new StockService(new CapturingRepository());
        Producto producto = producto(UnidadMedida.UN, 3);

        assertThrows(ValidationException.class,
                () -> service.registrar(producto, TipoMovimientoStock.SALIDA, "Merma", 4, null, null));
    }

    @Test
    void registrar_entradaValidaDelegaAlRepositorio() {
        CapturingRepository repository = new CapturingRepository();
        StockService service = new StockService(repository);
        Producto producto = producto(UnidadMedida.KG, 2.5);

        service.registrar(producto, TipoMovimientoStock.ENTRADA, " Compra ", 1.25, " FAC-1 ", " Lote nuevo ");

        assertEquals(8L, repository.productoId);
        assertEquals(TipoMovimientoStock.ENTRADA, repository.tipo);
        assertEquals("Compra", repository.motivo);
        assertEquals(1.25, repository.cantidad);
        assertEquals("FAC-1", repository.referencia);
    }

    private Producto producto(UnidadMedida unidad, double stock) {
        return new Producto(8L, "Producto", null, null, unidad, 1000, 700, stock, true);
    }

    private static final class CapturingRepository implements MovimientoStockRepository {
        private long productoId;
        private TipoMovimientoStock tipo;
        private String motivo;
        private double cantidad;
        private String referencia;

        @Override
        public void register(long productoId, TipoMovimientoStock tipo, String motivo, double cantidad,
                             String referencia, String observacion) {
            this.productoId = productoId;
            this.tipo = tipo;
            this.motivo = motivo;
            this.cantidad = cantidad;
            this.referencia = referencia;
        }
    }
}
