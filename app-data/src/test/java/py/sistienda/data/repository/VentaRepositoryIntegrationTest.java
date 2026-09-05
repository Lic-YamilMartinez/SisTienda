package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VentaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void registraVentaDetalleTicketYSalidaStockEnUnaTransaccion() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("venta-test.db"));
        new DatabaseInitializer(factory).initialize();

        var usuarioRepository = new SqliteUsuarioRepository(factory);
        var usuario = usuarioRepository.createOwner("admin", "hash");
        var cajaRepository = new SqliteCajaRepository(factory);
        var caja = cajaRepository.open(usuario.id(), 100000, null);
        var categoriaRepository = new SqliteCategoriaRepository(factory);
        var productoRepository = new SqliteProductoRepository(factory);
        var movimientoRepository = new SqliteMovimientoStockRepository(factory);

        var categoria = categoriaRepository.create("Ventas Test");
        var producto = productoRepository.create(new Producto(
                0L, "Gaseosa", categoria.id(), categoria.nombre(), UnidadMedida.UN,
                8000, 6000, 0, true
        ));
        movimientoRepository.register(producto.id(), TipoMovimientoStock.ENTRADA,
                "Carga inicial", 10, null, null);

        Producto conStock = productoRepository.findAllActive().stream()
                .filter(item -> item.id() == producto.id())
                .findFirst()
                .orElseThrow();

        var ventaRepository = new SqliteVentaRepository(factory);
        var result = ventaRepository.register(
                caja.id(),
                usuario.id(),
                MetodoPago.EFECTIVO,
                20000,
                4000,
                List.of(new LineaVenta(conStock, 2))
        );

        assertTrue(result.ventaId() > 0);
        assertEquals(1, result.nroTicket());
        assertEquals(16000d, result.total());
        assertEquals(4000d, result.gananciaTotal());

        Producto despues = productoRepository.findAllActive().stream()
                .filter(item -> item.id() == producto.id())
                .findFirst()
                .orElseThrow();
        assertEquals(8d, despues.stockActual());

        try (var connection = factory.open();
             var detail = connection.prepareStatement("SELECT COUNT(*) FROM venta_detalle WHERE venta_id = ?")) {
            detail.setLong(1, result.ventaId());
            try (var rows = detail.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }
}
