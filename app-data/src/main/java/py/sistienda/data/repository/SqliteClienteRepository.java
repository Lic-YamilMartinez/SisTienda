package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.AbonoClienteResultado;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.repository.ClienteRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SqliteClienteRepository implements ClienteRepository {
    private static final DateTimeFormatter SQLITE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double EPSILON = 0.000001d;
    public static final String CATEGORIA_COBRO_FIADO = "Cobro de fiado";

    private final SqliteConnectionFactory connectionFactory;

    public SqliteClienteRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public List<ClienteCuentaResumen> buscar(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String like = "%" + normalized + "%";
        String sql = """
                WITH movimientos AS (
                    SELECT v.cliente_id, v.fecha, v.total AS importe
                    FROM venta v
                    WHERE v.metodo_pago = 'FIADO' AND v.cliente_id IS NOT NULL
                    UNION ALL
                    SELECT v.cliente_id, a.fecha, -v.total AS importe
                    FROM venta_anulacion a
                    JOIN venta v ON v.id = a.venta_id
                    WHERE v.metodo_pago = 'FIADO' AND v.cliente_id IS NOT NULL
                    UNION ALL
                    SELECT v.cliente_id, d.fecha, -d.total AS importe
                    FROM devolucion d
                    JOIN venta v ON v.id = d.venta_id
                    WHERE v.metodo_pago = 'FIADO' AND v.cliente_id IS NOT NULL
                    UNION ALL
                    SELECT ca.cliente_id, ca.fecha, -ca.monto AS importe
                    FROM cliente_abono ca
                ),
                saldo AS (
                    SELECT cliente_id, COALESCE(SUM(importe), 0) AS saldo, MAX(fecha) AS ultimo_movimiento
                    FROM movimientos
                    GROUP BY cliente_id
                )
                SELECT c.id, c.nombre, c.documento, c.telefono, c.direccion, c.nota, c.activo,
                       COALESCE(s.saldo, 0) AS saldo, s.ultimo_movimiento
                FROM cliente c
                LEFT JOIN saldo s ON s.cliente_id = c.id
                WHERE c.activo = 1
                  AND (? = '' OR lower(c.nombre) LIKE ?
                       OR lower(COALESCE(c.documento, '')) LIKE ?
                       OR lower(COALESCE(c.telefono, '')) LIKE ?)
                ORDER BY CASE WHEN COALESCE(s.saldo, 0) > 0.000001 THEN 0 ELSE 1 END,
                         COALESCE(s.ultimo_movimiento, c.creado_en) DESC,
                         c.nombre COLLATE NOCASE
                LIMIT 500
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            statement.setString(2, like);
            statement.setString(3, like);
            statement.setString(4, like);
            try (var result = statement.executeQuery()) {
                List<ClienteCuentaResumen> items = new ArrayList<>();
                while (result.next()) {
                    String last = result.getString("ultimo_movimiento");
                    items.add(new ClienteCuentaResumen(
                            mapCliente(result),
                            result.getDouble("saldo"),
                            last == null ? null : parseDate(last)
                    ));
                }
                return List.copyOf(items);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar la cuenta de clientes.", e);
        }
    }

    @Override
    public Optional<Cliente> findById(long clienteId) {
        try (var connection = connectionFactory.open()) {
            return findById(connection, clienteId);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el cliente.", e);
        }
    }

    @Override
    public Cliente create(String nombre, String documento, String telefono, String direccion, String nota) {
        String sql = """
                INSERT INTO cliente (nombre, documento, telefono, direccion, nota, activo)
                VALUES (?, ?, ?, ?, ?, 1)
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nombre);
            statement.setString(2, documento);
            statement.setString(3, telefono);
            statement.setString(4, direccion);
            statement.setString(5, nota);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del cliente.");
                return findById(connection, keys.getLong(1)).orElseThrow();
            }
        } catch (SQLException e) {
            if (isDocumentoDuplicado(e)) {
                throw new ValidationException("Ya existe un cliente con ese documento o RUC.");
            }
            throw new RuntimeException("No se pudo crear el cliente.", e);
        }
    }

    @Override
    public Cliente update(long clienteId, String nombre, String documento, String telefono,
                          String direccion, String nota) {
        String sql = """
                UPDATE cliente
                SET nombre = ?, documento = ?, telefono = ?, direccion = ?, nota = ?, actualizado_en = datetime('now')
                WHERE id = ? AND activo = 1
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, nombre);
            statement.setString(2, documento);
            statement.setString(3, telefono);
            statement.setString(4, direccion);
            statement.setString(5, nota);
            statement.setLong(6, clienteId);
            if (statement.executeUpdate() != 1) {
                throw new ValidationException("No se encontró el cliente activo.");
            }
            return findById(connection, clienteId).orElseThrow();
        } catch (SQLException e) {
            if (isDocumentoDuplicado(e)) {
                throw new ValidationException("Ya existe un cliente con ese documento o RUC.");
            }
            throw new RuntimeException("No se pudo actualizar el cliente.", e);
        }
    }

    @Override
    public double saldo(long clienteId) {
        try (var connection = connectionFactory.open()) {
            ensureCliente(connection, clienteId);
            return saldo(connection, clienteId);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo calcular el saldo del cliente.", e);
        }
    }

    @Override
    public List<ClienteCuentaMovimiento> movimientos(long clienteId) {
        String sql = """
                SELECT fecha, tipo, referencia, cargo, abono, detalle, medio_pago
                FROM (
                    SELECT v.fecha AS fecha,
                           'VENTA FIADA' AS tipo,
                           'Ticket #' || v.nro_ticket AS referencia,
                           v.total AS cargo,
                           0 AS abono,
                           'Compra a crédito' AS detalle,
                           'FIADO' AS medio_pago,
                           v.id * 10 + 1 AS orden
                    FROM venta v
                    WHERE v.cliente_id = ? AND v.metodo_pago = 'FIADO'
                    UNION ALL
                    SELECT a.fecha,
                           'ANULACIÓN' AS tipo,
                           'Ticket #' || v.nro_ticket AS referencia,
                           0 AS cargo,
                           v.total AS abono,
                           'Venta anulada: ' || a.motivo AS detalle,
                           'FIADO' AS medio_pago,
                           v.id * 10 + 2 AS orden
                    FROM venta_anulacion a
                    JOIN venta v ON v.id = a.venta_id
                    WHERE v.cliente_id = ? AND v.metodo_pago = 'FIADO'
                    UNION ALL
                    SELECT d.fecha,
                           'DEVOLUCIÓN' AS tipo,
                           'Ticket #' || v.nro_ticket AS referencia,
                           0 AS cargo,
                           d.total AS abono,
                           'Devolución: ' || d.motivo AS detalle,
                           'FIADO' AS medio_pago,
                           d.id * 10 + 3 AS orden
                    FROM devolucion d
                    JOIN venta v ON v.id = d.venta_id
                    WHERE v.cliente_id = ? AND v.metodo_pago = 'FIADO'
                    UNION ALL
                    SELECT ca.fecha,
                           'ABONO' AS tipo,
                           'Abono #' || ca.id AS referencia,
                           0 AS cargo,
                           ca.monto AS abono,
                           COALESCE(ca.observacion, 'Cobro de cuenta') AS detalle,
                           ca.metodo_pago AS medio_pago,
                           ca.id * 10 + 4 AS orden
                    FROM cliente_abono ca
                    WHERE ca.cliente_id = ?
                ) m
                ORDER BY fecha DESC, orden DESC
                LIMIT 1000
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            ensureCliente(connection, clienteId);
            for (int i = 1; i <= 4; i++) statement.setLong(i, clienteId);
            try (var result = statement.executeQuery()) {
                List<ClienteCuentaMovimiento> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new ClienteCuentaMovimiento(
                            parseDate(result.getString("fecha")),
                            result.getString("tipo"),
                            result.getString("referencia"),
                            result.getDouble("cargo"),
                            result.getDouble("abono"),
                            result.getString("detalle"),
                            result.getString("medio_pago")
                    ));
                }
                return List.copyOf(items);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el historial de la cuenta.", e);
        }
    }

    @Override
    public AbonoClienteResultado registrarAbono(long clienteId, long cajaSesionId, long usuarioId,
                                                 MetodoPago metodoPago, double monto, String observacion) {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                Cliente cliente = ensureCliente(connection, clienteId);
                ensureCashOpen(connection, cajaSesionId, usuarioId);
                double saldoAnterior = saldo(connection, clienteId);
                if (saldoAnterior <= EPSILON) {
                    throw new ValidationException("El cliente ya no tiene deuda pendiente.");
                }
                if (monto - saldoAnterior > EPSILON) {
                    throw new ValidationException("El abono supera el saldo pendiente del cliente.");
                }

                long id = insertAbono(connection, clienteId, cajaSesionId, usuarioId, metodoPago, monto, observacion);
                if (metodoPago == MetodoPago.EFECTIVO) {
                    insertCashMovement(connection, id, cajaSesionId, usuarioId, cliente.nombre(), monto);
                }
                connection.commit();
                return new AbonoClienteResultado(id, monto, saldoAnterior, saldoAnterior - monto, metodoPago);
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                if (e instanceof ValidationException validationException) throw validationException;
                throw new RuntimeException("No se pudo registrar el abono del cliente.", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo abrir la transacción del abono.", e);
        }
    }

    private long insertAbono(Connection connection, long clienteId, long cajaSesionId, long usuarioId,
                             MetodoPago metodoPago, double monto, String observacion) throws SQLException {
        String sql = """
                INSERT INTO cliente_abono
                    (cliente_id, caja_sesion_id, usuario_id, metodo_pago, monto, observacion)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, clienteId);
            statement.setLong(2, cajaSesionId);
            statement.setLong(3, usuarioId);
            statement.setString(4, metodoPago.name());
            statement.setDouble(5, monto);
            statement.setString(6, observacion);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del abono.");
                return keys.getLong(1);
            }
        }
    }

    private void insertCashMovement(Connection connection, long abonoId, long cajaSesionId,
                                    long usuarioId, String cliente, double monto) throws SQLException {
        String sql = """
                INSERT INTO caja_movimiento
                    (caja_sesion_id, usuario_id, tipo, categoria, concepto, monto, referencia)
                VALUES (?, ?, 'INGRESO', ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, usuarioId);
            statement.setString(3, CATEGORIA_COBRO_FIADO);
            statement.setString(4, "Cobro de fiado · " + cliente);
            statement.setDouble(5, monto);
            statement.setString(6, "ABONO-" + abonoId);
            statement.executeUpdate();
        }
    }

    private double saldo(Connection connection, long clienteId) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(importe), 0) AS saldo
                FROM (
                    SELECT v.total AS importe
                    FROM venta v
                    WHERE v.cliente_id = ? AND v.metodo_pago = 'FIADO'
                    UNION ALL
                    SELECT -v.total
                    FROM venta_anulacion a
                    JOIN venta v ON v.id = a.venta_id
                    WHERE v.cliente_id = ? AND v.metodo_pago = 'FIADO'
                    UNION ALL
                    SELECT -d.total
                    FROM devolucion d
                    JOIN venta v ON v.id = d.venta_id
                    WHERE v.cliente_id = ? AND v.metodo_pago = 'FIADO'
                    UNION ALL
                    SELECT -ca.monto
                    FROM cliente_abono ca
                    WHERE ca.cliente_id = ?
                )
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) statement.setLong(i, clienteId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getDouble("saldo");
            }
        }
    }

    private Optional<Cliente> findById(Connection connection, long clienteId) throws SQLException {
        String sql = "SELECT id, nombre, documento, telefono, direccion, nota, activo FROM cliente WHERE id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, clienteId);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapCliente(result)) : Optional.empty();
            }
        }
    }

    private Cliente ensureCliente(Connection connection, long clienteId) throws SQLException {
        Cliente cliente = findById(connection, clienteId)
                .orElseThrow(() -> new ValidationException("No se encontró el cliente seleccionado."));
        if (!cliente.activo()) throw new ValidationException("El cliente está inactivo.");
        return cliente;
    }

    private void ensureCashOpen(Connection connection, long cajaSesionId, long usuarioId) throws SQLException {
        String sql = "SELECT 1 FROM caja_sesion WHERE id = ? AND usuario_id = ? AND estado = 'ABIERTA'";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cajaSesionId);
            statement.setLong(2, usuarioId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new ValidationException("La caja ya no está abierta.");
            }
        }
    }

    private Cliente mapCliente(ResultSet result) throws SQLException {
        return new Cliente(
                result.getLong("id"),
                result.getString("nombre"),
                result.getString("documento"),
                result.getString("telefono"),
                result.getString("direccion"),
                result.getString("nota"),
                result.getInt("activo") != 0
        );
    }

    private LocalDateTime parseDate(String value) {
        LocalDateTime utc = LocalDateTime.parse(value, SQLITE_DATE);
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private boolean isDocumentoDuplicado(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("uq_cliente_documento")) return true;
            if (message != null && message.toLowerCase(Locale.ROOT).contains("cliente.documento")) return true;
            current = current.getCause();
        }
        return false;
    }
}
