package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.PagoVenta;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.TipoMovimientoStock;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.service.ClienteService;
import py.sistienda.core.service.MovimientoCajaService;
import py.sistienda.core.service.VentaService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagoMixtoIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void ventaParcialReconoceVentaYGananciaHoyYElAbonoSoloMueveCaja() {
        Fixture fixture = fixture("parcial.db");

        var result = fixture.ventaService.venderMixto(
                fixture.owner,
                fixture.caja,
                List.of(new LineaVenta(fixture.producto, 1)),
                List.of(
                        new PagoVenta(MetodoPago.EFECTIVO, 20_000),
                        new PagoVenta(MetodoPago.FIADO, 5_000)
                ),
                fixture.cliente
        );

        assertEquals(MetodoPago.MIXTO, result.metodoPago());
        assertEquals(25_000d, result.total(), 0.001);
        assertEquals(20_000d, result.monto(MetodoPago.EFECTIVO), 0.001);
        assertEquals(5_000d, result.monto(MetodoPago.FIADO), 0.001);
        assertEquals(5_000d, fixture.clienteService.saldo(fixture.owner, fixture.cliente.id()), 0.001);

        var cajaAntes = fixture.cajaRepository.salesSummary(fixture.caja.id());
        assertEquals(20_000d, cajaAntes.efectivo(), 0.001);
        assertEquals(5_000d, cajaAntes.fiado(), 0.001);
        assertEquals(25_000d, cajaAntes.total(), 0.001);
        assertEquals(7_000d, cajaAntes.ganancia(), 0.001);

        var controlAntes = new MovimientoCajaService(new SqliteMovimientoCajaRepository(fixture.factory))
                .control(fixture.caja, cajaAntes.efectivo());
        assertEquals(120_000d, controlAntes.efectivoEsperado(), 0.001);

        var reporteAntes = new SqliteReporteRepository(fixture.factory)
                .resumenPeriodo(LocalDate.now(), LocalDate.now(), null);
        assertEquals(25_000d, reporteAntes.ventas(), 0.001);
        assertEquals(20_000d, reporteAntes.efectivo(), 0.001);
        assertEquals(5_000d, reporteAntes.fiado(), 0.001);
        assertEquals(7_000d, reporteAntes.gananciaComercial(), 0.001);

        var detalle = new SqliteReporteRepository(fixture.factory)
                .detalleVenta(result.ventaId()).orElseThrow();
        assertEquals(2, detalle.pagos().size());
        assertEquals(20_000d, detalle.monto(MetodoPago.EFECTIVO), 0.001);
        assertEquals(5_000d, detalle.monto(MetodoPago.FIADO), 0.001);

        fixture.clienteService.registrarAbono(
                fixture.owner,
                fixture.caja,
                fixture.cliente,
                MetodoPago.EFECTIVO,
                5_000,
                "Cancelación saldo"
        );

        assertEquals(0d, fixture.clienteService.saldo(fixture.owner, fixture.cliente.id()), 0.001);

        var cajaDespues = fixture.cajaRepository.salesSummary(fixture.caja.id());
        assertEquals(20_000d, cajaDespues.efectivo(), 0.001,
                "Cobrar una deuda no debe convertirse en una nueva venta en efectivo");
        assertEquals(7_000d, cajaDespues.ganancia(), 0.001,
                "Cobrar una deuda no debe generar ganancia nuevamente");

        var controlDespues = new MovimientoCajaService(new SqliteMovimientoCajaRepository(fixture.factory))
                .control(fixture.caja, cajaDespues.efectivo());
        assertEquals(125_000d, controlDespues.efectivoEsperado(), 0.001,
                "El abono en efectivo sí debe entrar al arqueo físico");

        var reporteDespues = new SqliteReporteRepository(fixture.factory)
                .resumenPeriodo(LocalDate.now(), LocalDate.now(), null);
        assertEquals(25_000d, reporteDespues.ventas(), 0.001);
        assertEquals(7_000d, reporteDespues.gananciaComercial(), 0.001);
        assertEquals(0d, reporteDespues.otrosIngresos(), 0.001,
                "El cobro de una cuenta no es otro ingreso operativo ni una segunda venta");
    }

    @Test
    void devolucionDeVentaMixtaReviertePrimeroFiadoYLuegoEfectivo() throws Exception {
        Fixture fixture = fixture("devolucion-mixta.db");

        var result = fixture.ventaService.venderMixto(
                fixture.owner,
                fixture.caja,
                List.of(new LineaVenta(fixture.producto, 1)),
                List.of(
                        new PagoVenta(MetodoPago.EFECTIVO, 20_000),
                        new PagoVenta(MetodoPago.FIADO, 5_000)
                ),
                fixture.cliente
        );

        var postventa = new SqlitePostventaRepository(fixture.factory);
        var venta = postventa.findVenta(result.ventaId()).orElseThrow();
        long detalleId = venta.lineas().getFirst().ventaDetalleId();

        postventa.devolver(
                result.ventaId(),
                fixture.caja.id(),
                fixture.owner.id(),
                "Cliente devuelve compra",
                List.of(new DevolucionLineaSolicitud(detalleId, 1))
        );

        assertEquals(0d, fixture.clienteService.saldo(fixture.owner, fixture.cliente.id()), 0.001);

        var caja = fixture.cajaRepository.salesSummary(fixture.caja.id());
        assertEquals(0d, caja.efectivo(), 0.001);
        assertEquals(0d, caja.fiado(), 0.001);
        assertEquals(0d, caja.total(), 0.001);
        assertEquals(0d, caja.ganancia(), 0.001);

        try (var connection = fixture.factory.open();
             var statement = connection.prepareStatement("""
                     SELECT metodo_pago, monto
                     FROM devolucion_pago
                     ORDER BY CASE metodo_pago WHEN 'FIADO' THEN 1 WHEN 'EFECTIVO' THEN 2 ELSE 9 END
                     """);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            assertEquals("FIADO", rows.getString("metodo_pago"));
            assertEquals(5_000d, rows.getDouble("monto"), 0.001);
            assertTrue(rows.next());
            assertEquals("EFECTIVO", rows.getString("metodo_pago"));
            assertEquals(20_000d, rows.getDouble("monto"), 0.001);
        }
    }

    private Fixture fixture(String file) {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve(file));
        new DatabaseInitializer(factory).initialize();

        var owner = new SqliteUsuarioRepository(factory).createOwner("owner", "hash");
        var cajaRepository = new SqliteCajaRepository(factory);
        var caja = cajaRepository.open(owner.id(), 100_000, null);

        var categoriaRepository = new SqliteCategoriaRepository(factory);
        var categoria = categoriaRepository.create("Prueba mixta");
        var productoRepository = new SqliteProductoRepository(factory);
        var creado = productoRepository.create(new Producto(
                0L,
                "Canasta prueba",
                categoria.id(),
                categoria.nombre(),
                UnidadMedida.UN,
                25_000,
                18_000,
                0,
                true
        ));
        new SqliteMovimientoStockRepository(factory).register(
                creado.id(),
                TipoMovimientoStock.ENTRADA,
                "Carga inicial",
                5,
                "QA-MIXTO",
                "Carga QA"
        );
        Producto producto = productoRepository.findAllActive().stream()
                .filter(item -> item.id() == creado.id())
                .findFirst()
                .orElseThrow();

        var clienteService = new ClienteService(
                new SqliteClienteRepository(factory),
                new AutorizacionService()
        );
        var cliente = clienteService.crear(
                owner,
                "Cliente prueba",
                "MIXTO-001",
                "0981000000",
                null,
                null
        );

        var ventaService = new VentaService(
                new SqliteVentaRepository(factory),
                clienteService
        );

        return new Fixture(
                factory,
                owner,
                caja,
                cajaRepository,
                producto,
                cliente,
                clienteService,
                ventaService
        );
    }

    private record Fixture(
            SqliteConnectionFactory factory,
            py.sistienda.core.model.Usuario owner,
            py.sistienda.core.model.CajaSesion caja,
            SqliteCajaRepository cajaRepository,
            Producto producto,
            py.sistienda.core.model.Cliente cliente,
            ClienteService clienteService,
            VentaService ventaService
    ) {
    }
}
