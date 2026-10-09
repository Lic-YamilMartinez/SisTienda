package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionClienteSaldoFila;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.service.ClienteService;
import py.sistienda.core.service.MigracionClienteService;
import py.sistienda.core.service.MovimientoCajaService;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigracionSaldoInicialIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void saldoInicialNoGeneraVentaGananciaStockNiCajaYPuedeCobrarDespues() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("saldo-inicial.db"));
        new DatabaseInitializer(factory).initialize();

        var owner = new SqliteUsuarioRepository(factory).createOwner("owner", "hash");
        var autorizacion = new AutorizacionService();
        var clienteRepository = new SqliteClienteRepository(factory);
        var clienteService = new ClienteService(clienteRepository, autorizacion);
        var migracionService = new MigracionClienteService(
                new SqliteMigracionClienteRepository(factory),
                autorizacion
        );

        var cliente = clienteService.crear(
                owner,
                "Maria Gonzalez",
                "1234567",
                "0981123456",
                "Barrio Centro",
                null
        );

        migracionService.registrarSaldoInicial(
                owner,
                cliente,
                120_000,
                LocalDate.of(2026, 10, 8),
                "Cuaderno anterior",
                "Deuda previa a SisTienda"
        );

        assertEquals(120_000d, clienteService.saldo(owner, cliente.id()), 0.001);

        var resumen = clienteService.buscar(owner, "Maria").getFirst();
        assertEquals(120_000d, resumen.saldo(), 0.001);
        assertEquals(120_000d, resumen.saldoInicial(), 0.001);
        assertEquals(120_000d, resumen.saldoInicialPendiente(), 0.001);
        assertEquals(0d, resumen.saldoSystiendaPendiente(), 0.001);

        var movimientos = clienteService.movimientos(owner, cliente.id());
        assertEquals(1, movimientos.size());
        assertEquals("SALDO INICIAL", movimientos.getFirst().tipo());
        assertEquals(120_000d, movimientos.getFirst().cargo(), 0.001);
        assertTrue(movimientos.getFirst().detalle().contains("Deuda anterior"));

        var reportes = new SqliteReporteRepository(factory);
        var reporteAntes = reportes.resumenPeriodo(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                null
        );
        assertEquals(0d, reporteAntes.ventas(), 0.001);
        assertEquals(0d, reporteAntes.gananciaComercial(), 0.001);

        try (var connection = factory.open(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) AS total FROM venta")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt("total"));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) AS total FROM mov_stock")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt("total"));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) AS total FROM caja_movimiento")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt("total"));
            }
        }

        var cajaRepository = new SqliteCajaRepository(factory);
        var caja = cajaRepository.open(owner.id(), 100_000, "Inicio piloto");

        clienteService.registrarAbono(
                owner,
                caja,
                cliente,
                MetodoPago.EFECTIVO,
                50_000,
                "Pago de deuda anterior"
        );

        assertEquals(70_000d, clienteService.saldo(owner, cliente.id()), 0.001);

        var despues = clienteService.buscar(owner, "Maria").getFirst();
        assertEquals(70_000d, despues.saldoInicialPendiente(), 0.001);
        assertEquals(0d, despues.saldoSystiendaPendiente(), 0.001);

        var ventasCaja = cajaRepository.salesSummary(caja.id());
        assertEquals(0d, ventasCaja.total(), 0.001);
        assertEquals(0d, ventasCaja.ganancia(), 0.001);

        var control = new MovimientoCajaService(new SqliteMovimientoCajaRepository(factory))
                .control(caja, ventasCaja.efectivo());
        assertEquals(150_000d, control.efectivoEsperado(), 0.001,
                "El cobro histórico sí debe ingresar al efectivo esperado de la caja");

        var reporteDespues = reportes.resumenPeriodo(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                null
        );
        assertEquals(0d, reporteDespues.ventas(), 0.001);
        assertEquals(0d, reporteDespues.gananciaComercial(), 0.001);
        assertEquals(0d, reporteDespues.otrosIngresos(), 0.001,
                "Cobrar cartera migrada no debe convertirse en otro ingreso operativo");

        assertThrows(ValidationException.class, () ->
                migracionService.registrarSaldoInicial(
                        owner,
                        cliente,
                        1_000,
                        LocalDate.of(2026, 10, 8),
                        "Duplicado",
                        null
                )
        );
    }

    @Test
    void importacionVinculaClienteExistenteCreaNuevosYNoDuplicaCartera() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(tempDir.resolve("importacion-saldos.db"));
        new DatabaseInitializer(factory).initialize();

        var owner = new SqliteUsuarioRepository(factory).createOwner("owner", "hash");
        var autorizacion = new AutorizacionService();
        var clienteRepository = new SqliteClienteRepository(factory);
        var clienteService = new ClienteService(clienteRepository, autorizacion);
        var migracionService = new MigracionClienteService(
                new SqliteMigracionClienteRepository(factory),
                autorizacion
        );

        clienteService.crear(
                owner,
                "Juan Perez",
                "1111111",
                "0981000001",
                null,
                null
        );

        List<ImportacionClienteSaldoFila> filas = List.of(
                new ImportacionClienteSaldoFila(
                        2, "Juan Perez", "1111111", "0981000001", "",
                        "75.000", "08/10/2026", "Cuaderno", "Cliente existente"
                ),
                new ImportacionClienteSaldoFila(
                        3, "Rosa Benitez", "2222222", "0981000002", "Barrio San Juan",
                        "300000", "08/10/2026", "Cuaderno", "Cliente nuevo"
                )
        );

        var validaciones = migracionService.validar(owner, filas);
        assertTrue(validaciones.stream().allMatch(item -> item.valida()));
        assertTrue(validaciones.getFirst().entrada().clienteExistente());
        assertTrue(!validaciones.get(1).entrada().clienteExistente());

        var resultado = migracionService.importar(owner, validaciones, "Migracion_tia.xlsx");
        assertEquals(1, resultado.clientesCreados());
        assertEquals(1, resultado.clientesExistentes());
        assertEquals(2, resultado.saldosInicialesCargados());
        assertEquals(375_000d, resultado.totalMigrado(), 0.001);

        var cuentas = clienteService.buscar(owner, "");
        assertEquals(2, cuentas.size());
        assertEquals(375_000d,
                cuentas.stream().mapToDouble(item -> item.saldo()).sum(),
                0.001);
        assertEquals(375_000d,
                cuentas.stream().mapToDouble(item -> item.saldoInicialPendiente()).sum(),
                0.001);

        var segundaValidacion = migracionService.validar(owner, filas);
        assertTrue(segundaValidacion.stream().noneMatch(item -> item.valida()),
                "Una segunda migración debe advertir que esos clientes ya tienen saldo inicial");

        assertThrows(ValidationException.class, () ->
                migracionService.importar(owner, validaciones, "Migracion_tia.xlsx")
        );
    }
}
