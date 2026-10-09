package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.FiltroReporte;
import py.sistienda.core.model.GranularidadReporte;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.TipoVentaReporte;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardEjecutivoIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void filtraPorClienteProductoPagoTipoYExponeCostoEnLaLineaDeTiempo() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("dashboard-ejecutivo.db"));
        new DatabaseInitializer(factory).initialize();

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO usuario (id, username, password_hash, rol, activo)
                    VALUES (1, 'owner', 'hash', 'DUENIO', 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO cliente (id, nombre, documento, activo)
                    VALUES (1, 'Cliente Ejecutivo', 'EJ-001', 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO producto (id, nombre, unidad_medida, precio_venta, costo, stock_actual, activo)
                    VALUES
                    (1, 'Producto A', 'UN', 25000, 18000, 10, 1),
                    (2, 'Producto B', 'UN', 10000, 6000, 10, 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO caja_sesion (id, usuario_id, fecha_apertura, monto_apertura, estado)
                    VALUES (1, 1, '2026-10-01 08:00:00', 100000, 'ABIERTA')
                    """);

            statement.executeUpdate("""
                    INSERT INTO venta (
                        id, caja_sesion_id, usuario_id, fecha, total, total_lista, ganancia_total,
                        metodo_pago, recibido, vuelto, nro_ticket, anulada, cliente_id
                    ) VALUES (
                        1, 1, 1, '2026-10-01 12:00:00', 25000, 25000, 7000,
                        'MIXTO', 20000, 0, 1, 0, 1
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO venta_pago (venta_id, metodo_pago, monto)
                    VALUES (1, 'EFECTIVO', 20000), (1, 'FIADO', 5000)
                    """);
            statement.executeUpdate("""
                    INSERT INTO venta_detalle (
                        venta_id, producto_id, cantidad, precio_unitario, precio_lista,
                        costo_unitario, subtotal, ganancia_linea
                    ) VALUES (1, 1, 1, 25000, 25000, 18000, 25000, 7000)
                    """);

            statement.executeUpdate("""
                    INSERT INTO venta (
                        id, caja_sesion_id, usuario_id, fecha, total, total_lista, ganancia_total,
                        metodo_pago, recibido, vuelto, nro_ticket, anulada
                    ) VALUES (
                        2, 1, 1, '2026-10-02 12:00:00', 10000, 10000, 4000,
                        'TRANSFERENCIA', 10000, 0, 2, 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO venta_detalle (
                        venta_id, producto_id, cantidad, precio_unitario, precio_lista,
                        costo_unitario, subtotal, ganancia_linea
                    ) VALUES (2, 2, 1, 10000, 10000, 6000, 10000, 4000)
                    """);
        }

        var repository = new SqliteReporteRepository(factory);
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 2);

        var cliente = new FiltroReporte(
                desde, hasta, GranularidadReporte.DIARIO,
                null, 1L, null, TipoVentaReporte.TODOS
        );
        var resumenCliente = repository.resumenPeriodo(cliente);
        assertEquals(25_000d, resumenCliente.ventas(), 0.001);
        assertEquals(18_000d, resumenCliente.costoMercaderia(), 0.001);
        assertEquals(7_000d, resumenCliente.gananciaComercial(), 0.001);
        assertEquals(1L, resumenCliente.tickets());

        var producto = new FiltroReporte(
                desde, hasta, GranularidadReporte.DIARIO,
                null, null, 2L, TipoVentaReporte.TODOS
        );
        var resumenProducto = repository.resumenPeriodo(producto);
        assertEquals(10_000d, resumenProducto.ventas(), 0.001);
        assertEquals(6_000d, resumenProducto.costoMercaderia(), 0.001);
        assertEquals(4_000d, resumenProducto.gananciaComercial(), 0.001);

        var efectivo = new FiltroReporte(
                desde, hasta, GranularidadReporte.DIARIO,
                MetodoPago.EFECTIVO, null, null, TipoVentaReporte.TODOS
        );
        var resumenEfectivo = repository.resumenPeriodo(efectivo);
        assertEquals(20_000d, resumenEfectivo.ventas(), 0.001);
        assertEquals(14_400d, resumenEfectivo.costoMercaderia(), 0.001);
        assertEquals(5_600d, resumenEfectivo.gananciaComercial(), 0.001);
        assertEquals(20_000d, resumenEfectivo.efectivo(), 0.001);

        var parcial = new FiltroReporte(
                desde, hasta, GranularidadReporte.DIARIO,
                null, null, null, TipoVentaReporte.MIXTO_PARCIAL
        );
        assertEquals(25_000d, repository.resumenPeriodo(parcial).ventas(), 0.001);

        var contado = new FiltroReporte(
                desde, hasta, GranularidadReporte.DIARIO,
                null, null, null, TipoVentaReporte.CONTADO
        );
        assertEquals(10_000d, repository.resumenPeriodo(contado).ventas(), 0.001);

        var timeline = repository.lineaTiempo(
                new FiltroReporte(
                        desde, hasta, GranularidadReporte.DIARIO,
                        null, null, null, TipoVentaReporte.TODOS
                )
        );
        assertEquals(2, timeline.size());
        assertEquals(25_000d, timeline.get(0).ventas(), 0.001);
        assertEquals(18_000d, timeline.get(0).costoMercaderia(), 0.001);
        assertEquals(7_000d, timeline.get(0).gananciaComercial(), 0.001);
        assertEquals(10_000d, timeline.get(1).ventas(), 0.001);
        assertEquals(6_000d, timeline.get(1).costoMercaderia(), 0.001);
        assertEquals(4_000d, timeline.get(1).gananciaComercial(), 0.001);

        assertTrue(repository.clientesDisponibles().stream()
                .anyMatch(item -> item.id() == 1L && item.etiqueta().contains("Cliente Ejecutivo")));
        assertTrue(repository.productosDisponibles().stream()
                .anyMatch(item -> item.id() == 1L && item.etiqueta().contains("Producto A")));

        var ventasCliente = repository.listarVentas(cliente, 20);
        assertEquals(1, ventasCliente.size());
        assertEquals("Cliente Ejecutivo", ventasCliente.getFirst().cliente());
    }
}
