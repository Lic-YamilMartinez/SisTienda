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
    void resumeVentasDeLaCajaPorMetodoDePago() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("resumen-caja-test.db"));
        new DatabaseInitializer(factory).initialize();

        var usuario = new SqliteUsuarioRepository(factory).createOwner("admin", "hash");
        var repository = new SqliteCajaRepository(factory);
        var caja = repository.open(usuario.id(), 100000, null);

        try (var connection = factory.open();
             var statement = connection.prepareStatement("""
                     INSERT INTO venta (
                         caja_sesion_id, usuario_id, total, total_lista, ganancia_total,
                         metodo_pago, recibido, vuelto, anulada
                     ) VALUES (?, ?, ?, ?, 0, ?, ?, 0, ?)
                     """)) {
            insertarVenta(statement, caja.id(), usuario.id(), 50000, "EFECTIVO", 50000, false);
            insertarVenta(statement, caja.id(), usuario.id(), 70000, "TRANSFERENCIA", 70000, false);
            insertarVenta(statement, caja.id(), usuario.id(), 30000, "TARJETA", 30000, false);
            insertarVenta(statement, caja.id(), usuario.id(), 99999, "EFECTIVO", 99999, true);
        }

        var resumen = repository.salesSummary(caja.id());
        assertEquals(50000d, resumen.efectivo());
        assertEquals(70000d, resumen.transferencia());
        assertEquals(30000d, resumen.tarjeta());
        assertEquals(150000d, resumen.total());
    }

    private void insertarVenta(
            java.sql.PreparedStatement statement,
            long cajaId,
            long usuarioId,
            double total,
            String metodo,
            double recibido,
            boolean anulada
    ) throws Exception {
        statement.setLong(1, cajaId);
        statement.setLong(2, usuarioId);
        statement.setDouble(3, total);
        statement.setDouble(4, total);
        statement.setString(5, metodo);
        statement.setDouble(6, recibido);
        statement.setInt(7, anulada ? 1 : 0);
        statement.executeUpdate();
    }
}
