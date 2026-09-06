package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArqueoCajaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void consolidaVentasMovimientosYDetectaFaltante() throws Exception {
        var factory = new SqliteConnectionFactory(tempDir.resolve("arqueo.db"));
        new DatabaseInitializer(factory).initialize();

        long usuarioId;
        long cajaId;
        try (var connection = factory.open()) {
            try (var st = connection.prepareStatement(
                    "INSERT INTO usuario (username, password_hash, rol, activo) VALUES ('admin', 'hash', 'DUENIO', 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                st.executeUpdate();
                try (var keys = st.getGeneratedKeys()) { assertTrue(keys.next()); usuarioId = keys.getLong(1); }
            }

            try (var st = connection.prepareStatement(
                    "INSERT INTO caja_sesion (usuario_id, monto_apertura, monto_cierre, estado, fecha_cierre, notas) VALUES (?, 100000, 235000, 'CERRADA', datetime('now'), 'Faltante explicado')",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                st.setLong(1, usuarioId);
                st.executeUpdate();
                try (var keys = st.getGeneratedKeys()) { assertTrue(keys.next()); cajaId = keys.getLong(1); }
            }

            try (var st = connection.prepareStatement(
                    "INSERT INTO venta (caja_sesion_id, usuario_id, total, metodo_pago, recibido, vuelto, nro_ticket, anulada) VALUES (?, ?, ?, ?, ?, 0, ?, 0)")) {
                st.setLong(1, cajaId); st.setLong(2, usuarioId); st.setDouble(3, 150000); st.setString(4, "EFECTIVO"); st.setDouble(5, 150000); st.setLong(6, 1); st.executeUpdate();
                st.setLong(1, cajaId); st.setLong(2, usuarioId); st.setDouble(3, 50000); st.setString(4, "TRANSFERENCIA"); st.setDouble(5, 0); st.setLong(6, 2); st.executeUpdate();
            }

            try (var st = connection.prepareStatement(
                    "INSERT INTO caja_movimiento (caja_sesion_id, usuario_id, tipo, categoria, concepto, monto) VALUES (?, ?, ?, ?, ?, ?)")) {
                st.setLong(1, cajaId); st.setLong(2, usuarioId); st.setString(3, "INGRESO"); st.setString(4, "Aporte"); st.setString(5, "Cambio adicional"); st.setDouble(6, 20000); st.executeUpdate();
                st.setLong(1, cajaId); st.setLong(2, usuarioId); st.setString(3, "EGRESO"); st.setString(4, "Flete"); st.setString(5, "Entrega"); st.setDouble(6, 30000); st.executeUpdate();
            }
        }

        var repository = new SqliteArqueoCajaRepository(factory);
        var historial = repository.findRecent(10);
        assertEquals(1, historial.size());
        var resumen = historial.getFirst();
        assertEquals(EstadoCaja.CERRADA, resumen.estado());
        assertEquals(200000, resumen.totalVendido(), 0.001);
        assertEquals(2, resumen.tickets());
        assertEquals(240000, resumen.efectivoEsperado(), 0.001);
        assertEquals(235000, resumen.efectivoContado(), 0.001);
        assertEquals(-5000, resumen.diferencia(), 0.001);

        var detalle = repository.findDetail(cajaId);
        assertEquals(150000, detalle.ventas().efectivo(), 0.001);
        assertEquals(50000, detalle.ventas().transferencia(), 0.001);
        assertEquals(20000, detalle.movimientos().ingresos(), 0.001);
        assertEquals(30000, detalle.movimientos().egresos(), 0.001);
        assertEquals(240000, detalle.efectivoEsperado(), 0.001);
        assertEquals(-5000, detalle.diferencia(), 0.001);
        assertEquals("Faltante explicado", detalle.notas());
        assertEquals(2, detalle.detalleMovimientos().size());
    }
}
