package py.sistienda.data.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import py.sistienda.core.model.TipoMovimientoCaja;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MovimientoCajaRepositoryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void registraListaYResumeIngresosYEgresosDeUnaCaja() throws Exception {
        var factory = new SqliteConnectionFactory(tempDir.resolve("caja-movimientos.db"));
        new DatabaseInitializer(factory).initialize();

        long usuarioId;
        long cajaId;
        try (var connection = factory.open()) {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO usuario (username, password_hash, rol, activo) VALUES ('admin', 'hash', 'DUENIO', 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); usuarioId = keys.getLong(1); }
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO caja_sesion (usuario_id, monto_apertura, estado) VALUES (?, 200000, 'ABIERTA')",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, usuarioId);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); cajaId = keys.getLong(1); }
            }
        }

        var repo = new SqliteMovimientoCajaRepository(factory);
        repo.create(cajaId, usuarioId, TipoMovimientoCaja.INGRESO, "Aporte", "Refuerzo de caja", 30000, "AP-1");
        repo.create(cajaId, usuarioId, TipoMovimientoCaja.EGRESO, "Flete", "Entrega proveedor", 15000, "REC-8");
        repo.create(cajaId, usuarioId, TipoMovimientoCaja.EGRESO, "Luz", "Factura ANDE", 25000, null);

        var movimientos = repo.findByCaja(cajaId);
        assertEquals(3, movimientos.size());
        assertEquals("admin", movimientos.getFirst().usuario());

        var resumen = repo.summary(cajaId);
        assertEquals(30000, resumen.ingresos(), 0.001);
        assertEquals(40000, resumen.egresos(), 0.001);
    }
}
