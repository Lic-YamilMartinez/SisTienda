package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.service.ClienteService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteFiadoIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void ventaFiadaGeneraDeudaYAbonoEfectivoEntraACajaSinDuplicarIngreso() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("fiado.db"));
        new DatabaseInitializer(factory).initialize();
        seedUserAndCash(factory);

        Usuario owner = new Usuario(1, "owner", "DUENIO", true);
        CajaSesion caja = new CajaSesion(1, 1, LocalDateTime.now(), null, 100_000d, null, EstadoCaja.ABIERTA, null);
        ClienteService service = new ClienteService(new SqliteClienteRepository(factory), new AutorizacionService());
        var cliente = service.crear(owner, "María González", "1234567", "0981000000", null, null);

        try (var connection = factory.open();
             var statement = connection.prepareStatement("""
                     INSERT INTO venta
                         (caja_sesion_id, usuario_id, total, total_lista, ganancia_total,
                          metodo_pago, recibido, vuelto, nro_ticket, anulada, cliente_id)
                     VALUES (1, 1, 100000, 100000, 25000, 'FIADO', 0, 0, 1, 0, ?)
                     """)) {
            statement.setLong(1, cliente.id());
            statement.executeUpdate();
        }

        assertEquals(100_000d, service.saldo(owner, cliente.id()), 0.001d);

        var abono = service.registrarAbono(owner, caja, cliente, MetodoPago.EFECTIVO, 40_000d, "Entrega parcial");
        assertEquals(100_000d, abono.saldoAnterior(), 0.001d);
        assertEquals(60_000d, abono.saldoNuevo(), 0.001d);
        assertEquals(60_000d, service.saldo(owner, cliente.id()), 0.001d);

        try (var connection = factory.open();
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT tipo, categoria, monto, referencia
                     FROM caja_movimiento
                     WHERE caja_sesion_id = 1
                     """)) {
            assertTrue(result.next());
            assertEquals("INGRESO", result.getString("tipo"));
            assertEquals(SqliteClienteRepository.CATEGORIA_COBRO_FIADO, result.getString("categoria"));
            assertEquals(40_000d, result.getDouble("monto"), 0.001d);
            assertEquals("ABONO-" + abono.id(), result.getString("referencia"));
        }

        var reporte = new SqliteReporteRepository(factory)
                .resumenPeriodo(LocalDate.now(), LocalDate.now(), null);
        assertEquals(100_000d, reporte.ventas(), 0.001d);
        assertEquals(100_000d, reporte.fiado(), 0.001d);
        assertEquals(0d, reporte.otrosIngresos(), 0.001d,
                "Cobrar una cuenta por cobrar no debe volver a contarse como ingreso operativo");

        assertThrows(ValidationException.class,
                () -> service.registrarAbono(owner, caja, cliente, MetodoPago.EFECTIVO, 70_000d, null));
    }

    private void seedUserAndCash(SqliteConnectionFactory factory) throws Exception {
        try (var connection = factory.open(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO usuario (id, username, password_hash, rol, activo)
                    VALUES (1, 'owner', 'hash', 'DUENIO', 1)
                    """);
            statement.execute("""
                    INSERT INTO caja_sesion (id, usuario_id, monto_apertura, estado)
                    VALUES (1, 1, 100000, 'ABIERTA')
                    """);
        }
    }
}
