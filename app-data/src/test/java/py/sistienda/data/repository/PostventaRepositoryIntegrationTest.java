package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.DevolucionLineaSolicitud;
import py.sistienda.core.model.LineaVenta;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Producto;
import py.sistienda.core.model.UnidadMedida;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostventaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void devolucionParcialReponeStockYRestaCajaReportesYGanancia() throws Exception {
        SqliteConnectionFactory factory = prepararBase("devolucion.db");
        var ventaRepository = new SqliteVentaRepository(factory);
        Producto producto = producto();

        var venta = ventaRepository.register(
                1, 1, MetodoPago.EFECTIVO, 20000, 0,
                List.of(new LineaVenta(producto, 2))
        );
        assertEquals(8d, stock(factory), 0.001);

        var postventa = new SqlitePostventaRepository(factory);
        var ventaPostventa = postventa.findVenta(venta.ventaId()).orElseThrow();
        long detalleId = ventaPostventa.lineas().getFirst().ventaDetalleId();

        var devolucion = postventa.devolver(
                venta.ventaId(), 1, 1, "Producto defectuoso",
                List.of(new DevolucionLineaSolicitud(detalleId, 1))
        );

        assertEquals(10000d, devolucion.totalDevuelto(), 0.001);
        assertEquals(6000d, devolucion.costoRevertido(), 0.001);
        assertEquals(4000d, devolucion.gananciaRevertida(), 0.001);
        assertEquals(9d, stock(factory), 0.001);

        var caja = new SqliteCajaRepository(factory).salesSummary(1);
        assertEquals(10000d, caja.efectivo(), 0.001);
        assertEquals(10000d, caja.total(), 0.001);

        var reporte = new SqliteReporteRepository(factory)
                .resumenPeriodo(LocalDate.now(), LocalDate.now(), null);
        assertEquals(10000d, reporte.ventas(), 0.001);
        assertEquals(6000d, reporte.costoMercaderia(), 0.001);
        assertEquals(4000d, reporte.gananciaComercial(), 0.001);

        var actualizada = postventa.findVenta(venta.ventaId()).orElseThrow();
        assertEquals(10000d, actualizada.totalDevuelto(), 0.001);
        assertEquals(1d, actualizada.lineas().getFirst().cantidadDisponible(), 0.001);
        assertThrows(ValidationException.class,
                () -> postventa.anular(venta.ventaId(), 1, "Ya no corresponde"));
    }

    @Test
    void anulacionConCajaOriginalAbiertaReponeTodoYQuedaAuditada() throws Exception {
        SqliteConnectionFactory factory = prepararBase("anulacion.db");
        var ventaRepository = new SqliteVentaRepository(factory);
        var venta = ventaRepository.register(
                1, 1, MetodoPago.TRANSFERENCIA, 20000, 0,
                List.of(new LineaVenta(producto(), 2))
        );
        assertEquals(8d, stock(factory), 0.001);

        var postventa = new SqlitePostventaRepository(factory);
        postventa.anular(venta.ventaId(), 1, "Venta cargada por error");

        assertEquals(10d, stock(factory), 0.001);
        var actualizada = postventa.findVenta(venta.ventaId()).orElseThrow();
        assertTrue(actualizada.anulada());
        assertEquals("Venta cargada por error", actualizada.motivoAnulacion());

        var reporte = new SqliteReporteRepository(factory)
                .resumenPeriodo(LocalDate.now(), LocalDate.now(), null);
        assertEquals(0d, reporte.ventas(), 0.001);
        assertEquals(0d, reporte.gananciaComercial(), 0.001);

        try (var connection = factory.open();
             var result = connection.createStatement().executeQuery(
                     "SELECT COUNT(*) AS cantidad FROM venta_anulacion WHERE venta_id = " + venta.ventaId())) {
            assertTrue(result.next());
            assertEquals(1, result.getInt("cantidad"));
        }
    }

    @Test
    void noAnulaVentaDeCajaCerradaParaNoReescribirArqueoHistorico() throws Exception {
        SqliteConnectionFactory factory = prepararBase("cerrada.db");
        var ventaRepository = new SqliteVentaRepository(factory);
        var venta = ventaRepository.register(
                1, 1, MetodoPago.EFECTIVO, 20000, 0,
                List.of(new LineaVenta(producto(), 2))
        );
        try (var connection = factory.open()) {
            connection.createStatement().executeUpdate(
                    "UPDATE caja_sesion SET estado = 'CERRADA', fecha_cierre = datetime('now'), monto_cierre = 120000 WHERE id = 1"
            );
        }

        var postventa = new SqlitePostventaRepository(factory);
        assertThrows(ValidationException.class,
                () -> postventa.anular(venta.ventaId(), 1, "Error detectado tarde"));
        assertEquals(8d, stock(factory), 0.001);
        assertFalse(postventa.findVenta(venta.ventaId()).orElseThrow().anulada());
    }

    private SqliteConnectionFactory prepararBase(String file) throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve(file));
        new DatabaseInitializer(factory).initialize();
        try (var connection = factory.open()) {
            connection.createStatement().executeUpdate(
                    "INSERT INTO usuario (id, username, password_hash, rol, activo) VALUES (1, 'owner', 'hash', 'DUENIO', 1)"
            );
            connection.createStatement().executeUpdate(
                    "INSERT INTO producto (id, nombre, unidad_medida, precio_venta, costo, stock_actual, activo) "
                            + "VALUES (1, 'Producto', 'UN', 10000, 6000, 10, 1)"
            );
            connection.createStatement().executeUpdate(
                    "INSERT INTO caja_sesion (id, usuario_id, monto_apertura, estado) VALUES (1, 1, 100000, 'ABIERTA')"
            );
        }
        return factory;
    }

    private Producto producto() {
        return new Producto(1, "Producto", null, null, UnidadMedida.UN, 10000, 6000, 10, true);
    }

    private double stock(SqliteConnectionFactory factory) throws Exception {
        try (var connection = factory.open();
             var result = connection.createStatement().executeQuery("SELECT stock_actual FROM producto WHERE id = 1")) {
            result.next();
            return result.getDouble(1);
        }
    }
}
