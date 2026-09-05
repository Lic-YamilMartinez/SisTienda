package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReporteRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void calculaResumenListaVentasYAbreDetalleDeTicket() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("reportes-test.db"));
        new DatabaseInitializer(factory).initialize();

        try (var connection = factory.open()) {
            connection.createStatement().executeUpdate("INSERT INTO usuario (username, password_hash, rol, activo) VALUES ('admin', 'hash', 'DUENIO', 1)");
            connection.createStatement().executeUpdate("INSERT INTO producto (nombre, unidad_medida, precio_venta, costo, stock_actual, activo) VALUES ('Producto A', 'UN', 100000, 70000, 10, 1)");
            connection.createStatement().executeUpdate("INSERT INTO caja_sesion (usuario_id, fecha_apertura, monto_apertura, estado) VALUES (1, '2026-09-05 08:00:00', 100000, 'ABIERTA')");

            connection.createStatement().executeUpdate("INSERT INTO venta (caja_sesion_id, usuario_id, fecha, total, total_lista, ganancia_total, metodo_pago, recibido, vuelto, nro_ticket, anulada) VALUES (1,1,'2026-09-05 12:00:00',100000,100000,30000,'EFECTIVO',120000,20000,1,0)");
            connection.createStatement().executeUpdate("INSERT INTO venta (caja_sesion_id, usuario_id, fecha, total, total_lista, ganancia_total, metodo_pago, recibido, vuelto, nro_ticket, anulada) VALUES (1,1,'2026-09-05 13:00:00',50000,50000,15000,'TRANSFERENCIA',0,0,2,0)");
            connection.createStatement().executeUpdate("INSERT INTO venta (caja_sesion_id, usuario_id, fecha, total, total_lista, ganancia_total, metodo_pago, recibido, vuelto, nro_ticket, anulada) VALUES (1,1,'2026-09-05 14:00:00',25000,25000,5000,'TARJETA',0,0,3,0)");
            connection.createStatement().executeUpdate("INSERT INTO venta (caja_sesion_id, usuario_id, fecha, total, total_lista, ganancia_total, metodo_pago, recibido, vuelto, nro_ticket, anulada) VALUES (1,1,'2026-09-05 15:00:00',90000,90000,30000,'EFECTIVO',90000,0,4,1)");
            connection.createStatement().executeUpdate("INSERT INTO venta_detalle (venta_id, producto_id, cantidad, precio_unitario, precio_lista, costo_unitario, subtotal, ganancia_linea) VALUES (1,1,1,100000,100000,70000,100000,30000)");
        }

        var repository = new SqliteReporteRepository(factory);
        LocalDate fecha = LocalDate.of(2026, 9, 5);
        var resumen = repository.resumenDiario(fecha);

        assertEquals(175000d, resumen.ventas(), 0.001);
        assertEquals(50000d, resumen.ganancia(), 0.001);
        assertEquals(3L, resumen.tickets());
        assertEquals(175000d / 3d, resumen.ticketPromedio(), 0.001);
        assertEquals(100000d, resumen.efectivo(), 0.001);
        assertEquals(50000d, resumen.transferencia(), 0.001);
        assertEquals(25000d, resumen.tarjeta(), 0.001);

        var ventas = repository.listarVentas(fecha);
        assertEquals(4, ventas.size());
        assertTrue(ventas.stream().anyMatch(v -> v.anulada() && v.nroTicket() == 4));
        assertTrue(ventas.stream().anyMatch(v -> v.metodoPago() == MetodoPago.TRANSFERENCIA));

        var detalle = repository.detalleVenta(1L).orElseThrow();
        assertEquals(1L, detalle.nroTicket());
        assertEquals("admin", detalle.usuario());
        assertEquals(MetodoPago.EFECTIVO, detalle.metodoPago());
        assertEquals(100000d, detalle.total(), 0.001);
        assertEquals(30000d, detalle.ganancia(), 0.001);
        assertFalse(detalle.anulada());
        assertEquals(1, detalle.items().size());
        assertEquals("Producto A", detalle.items().get(0).producto());
    }
}
