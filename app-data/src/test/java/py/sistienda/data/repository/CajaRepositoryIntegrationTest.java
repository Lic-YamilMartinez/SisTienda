package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CajaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void abreConsultaYCierraCajaEnSqliteReal() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("caja-test.db"));
        new DatabaseInitializer(factory).initialize();

        var usuarioRepository = new SqliteUsuarioRepository(factory);
        var usuario = usuarioRepository.createOwner("admin", "hash");
        var repository = new SqliteCajaRepository(factory);

        assertFalse(repository.findOpenByUser(usuario.id()).isPresent());

        var abierta = repository.open(usuario.id(), 150000, "Inicio");
        assertTrue(abierta.abierta());
        assertEquals(150000d, abierta.montoApertura());
        assertTrue(repository.findOpenByUser(usuario.id()).isPresent());

        var cerrada = repository.close(abierta.id(), 250000, "Cierre");
        assertEquals(EstadoCaja.CERRADA, cerrada.estado());
        assertEquals(250000d, cerrada.montoCierre());
        assertFalse(repository.findOpenByUser(usuario.id()).isPresent());
    }

    @Test
    void resumeVentasFiadoYGananciaNetaDeLaCaja() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("resumen-caja-test.db"));
        new DatabaseInitializer(factory).initialize();

        var usuario = new SqliteUsuarioRepository(factory).createOwner("admin", "hash");
        var repository = new SqliteCajaRepository(factory);
        var caja = repository.open(usuario.id(), 100000, null);

        long ventaEfectivoId;
        try (var connection = factory.open();
             var statement = connection.prepareStatement("""
                     INSERT INTO venta (
                         caja_sesion_id, usuario_id, total, total_lista, ganancia_total,
                         metodo_pago, recibido, vuelto, anulada
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                     """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ventaEfectivoId = insertarVenta(statement, caja.id(), usuario.id(), 50000, 20000, "EFECTIVO", 50000, false);
            insertarVenta(statement, caja.id(), usuario.id(), 70000, 30000, "TRANSFERENCIA", 70000, false);
            insertarVenta(statement, caja.id(), usuario.id(), 30000, 10000, "TARJETA", 30000, false);
            insertarVenta(statement, caja.id(), usuario.id(), 40000, 15000, "FIADO", 0, false);
            insertarVenta(statement, caja.id(), usuario.id(), 99999, 99999, "EFECTIVO", 99999, true);
        }

        try (var connection = factory.open();
             var statement = connection.prepareStatement("""
                     INSERT INTO devolucion (
                         venta_id, caja_sesion_id, usuario_id, metodo_pago,
                         total, costo_total, ganancia_revertida, motivo
                     ) VALUES (?, ?, ?, 'EFECTIVO', 10000, 6000, 4000, 'Prueba')
                     """)) {
            statement.setLong(1, ventaEfectivoId);
            statement.setLong(2, caja.id());
            statement.setLong(3, usuario.id());
            statement.executeUpdate();
        }

        var resumen = repository.salesSummary(caja.id());
        assertEquals(40000d, resumen.efectivo());
        assertEquals(70000d, resumen.transferencia());
        assertEquals(30000d, resumen.tarjeta());
        assertEquals(40000d, resumen.fiado());
        assertEquals(180000d, resumen.total());
        assertEquals(71000d, resumen.ganancia());
    }

    private long insertarVenta(
            java.sql.PreparedStatement statement,
            long cajaId,
            long usuarioId,
            double total,
            double ganancia,
            String metodo,
            double recibido,
            boolean anulada
    ) throws Exception {
        statement.setLong(1, cajaId);
        statement.setLong(2, usuarioId);
        statement.setDouble(3, total);
        statement.setDouble(4, total);
        statement.setDouble(5, ganancia);
        statement.setString(6, metodo);
        statement.setDouble(7, recibido);
        statement.setInt(8, anulada ? 1 : 0);
        statement.executeUpdate();
        try (var keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new IllegalStateException("No se generó id de venta");
            return keys.getLong(1);
        }
    }
}
