package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ImportacionClienteSaldoEntrada;
import py.sistienda.core.model.ImportacionClienteSaldoResultado;
import py.sistienda.core.model.MigracionClienteCoincidencia;
import py.sistienda.core.model.SaldoInicialClienteResultado;
import py.sistienda.core.repository.MigracionClienteRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SqliteMigracionClienteRepository implements MigracionClienteRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteMigracionClienteRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public MigracionClienteCoincidencia buscarCoincidencia(String nombre, String documento, String telefono) {
        try (var connection = connectionFactory.open()) {
            Long clienteId = resolverCliente(connection, nombre, documento, telefono);
            if (clienteId == null) return MigracionClienteCoincidencia.nueva();
            return new MigracionClienteCoincidencia(
                    clienteId,
                    tieneSaldoInicial(connection, clienteId)
            );
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo validar el cliente de la migración.", e);
        }
    }

    @Override
    public SaldoInicialClienteResultado registrarSaldoInicial(
            long clienteId,
            long usuarioId,
            double monto,
            LocalDate fechaReferencia,
            String referencia,
            String observacion
    ) {
        try (var connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                asegurarClienteActivo(connection, clienteId);
                asegurarSinSaldoInicial(connection, clienteId);
                long id = insertarSaldo(
                        connection,
                        clienteId,
                        usuarioId,
                        monto,
                        fechaReferencia,
                        referencia,
                        observacion,
                        "MANUAL"
                );
                connection.commit();
                return new SaldoInicialClienteResultado(
                        id, clienteId, monto, fechaReferencia, referencia, observacion
                );
            } catch (RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            } catch (SQLException e) {
                rollbackQuietly(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            if (esSaldoDuplicado(e)) {
                throw new ValidationException("Este cliente ya tiene un saldo inicial migrado.");
            }
            throw new RuntimeException("No se pudo registrar el saldo inicial del cliente.", e);
        }
    }

    @Override
    public ImportacionClienteSaldoResultado importarSaldosIniciales(
            List<ImportacionClienteSaldoEntrada> entradas,
            long usuarioId,
            String referenciaLote
    ) {
        Objects.requireNonNull(entradas);
        try (var connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try {
                int creados = 0;
                int existentes = 0;
                int saldos = 0;
                double total = 0d;

                for (ImportacionClienteSaldoEntrada entrada : entradas) {
                    Long clienteId = entrada.clienteExistenteId();
                    if (clienteId == null || clienteId <= 0) {
                        clienteId = resolverCliente(
                                connection,
                                entrada.nombre(),
                                entrada.documento(),
                                entrada.telefono()
                        );
                    }

                    if (clienteId == null) {
                        clienteId = insertarCliente(connection, entrada);
                        creados++;
                    } else {
                        asegurarClienteActivo(connection, clienteId);
                        existentes++;
                    }

                    asegurarSinSaldoInicial(connection, clienteId);
                    String referencia = combinarReferencia(referenciaLote, entrada.referencia());
                    insertarSaldo(
                            connection,
                            clienteId,
                            usuarioId,
                            entrada.saldoInicial(),
                            entrada.fechaReferencia(),
                            referencia,
                            entrada.observacion(),
                            "IMPORTACION"
                    );
                    saldos++;
                    total += entrada.saldoInicial();
                }

                connection.commit();
                return new ImportacionClienteSaldoResultado(
                        creados,
                        existentes,
                        saldos,
                        total
                );
            } catch (RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            } catch (SQLException e) {
                rollbackQuietly(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            if (esSaldoDuplicado(e)) {
                throw new ValidationException(
                        "La migración encontró un cliente con saldo inicial ya cargado. Revisá la vista previa."
                );
            }
            if (esDocumentoDuplicado(e)) {
                throw new ValidationException(
                        "La migración encontró un documento/RUC duplicado. Revisá el archivo antes de continuar."
                );
            }
            throw new RuntimeException("No se pudo completar la migración inicial de clientes.", e);
        }
    }

    private Long resolverCliente(
            Connection connection,
            String nombre,
            String documento,
            String telefono
    ) throws SQLException {
        if (documento != null && !documento.isBlank()) {
            String sql = """
                    SELECT id
                    FROM cliente
                    WHERE lower(trim(documento)) = lower(trim(?))
                    LIMIT 1
                    """;
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, documento);
                try (var result = statement.executeQuery()) {
                    if (result.next()) return result.getLong("id");
                }
            }
        }

        if (telefono == null || telefono.isBlank() || nombre == null || nombre.isBlank()) {
            return null;
        }

        String sql = """
                SELECT id
                FROM cliente
                WHERE lower(trim(nombre)) = lower(trim(?))
                  AND lower(trim(COALESCE(telefono, ''))) = lower(trim(?))
                ORDER BY activo DESC, id
                LIMIT 1
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, nombre);
            statement.setString(2, telefono);
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong("id") : null;
            }
        }
    }

    private long insertarCliente(Connection connection, ImportacionClienteSaldoEntrada entrada)
            throws SQLException {
        String sql = """
                INSERT INTO cliente (
                    nombre, documento, telefono, direccion, nota, activo
                ) VALUES (?, ?, ?, ?, ?, 1)
                """;
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, entrada.nombre());
            statement.setString(2, entrada.documento());
            statement.setString(3, entrada.telefono());
            statement.setString(4, entrada.direccion());
            statement.setString(5, "Cliente migrado con saldo inicial");
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del cliente migrado.");
                return keys.getLong(1);
            }
        }
    }

    private long insertarSaldo(
            Connection connection,
            long clienteId,
            long usuarioId,
            double monto,
            LocalDate fechaReferencia,
            String referencia,
            String observacion,
            String origen
    ) throws SQLException {
        String sql = """
                INSERT INTO cliente_saldo_inicial (
                    cliente_id, usuario_id, monto, fecha_referencia,
                    referencia, observacion, origen
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, clienteId);
            statement.setLong(2, usuarioId);
            statement.setDouble(3, monto);
            statement.setString(4, fechaReferencia.toString());
            statement.setString(5, referencia);
            statement.setString(6, observacion);
            statement.setString(7, origen);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del saldo inicial.");
                return keys.getLong(1);
            }
        }
    }

    private void asegurarClienteActivo(Connection connection, long clienteId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT activo FROM cliente WHERE id = ? LIMIT 1")) {
            statement.setLong(1, clienteId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new ValidationException("No se encontró el cliente seleccionado.");
                if (result.getInt("activo") == 0) {
                    throw new ValidationException("El cliente seleccionado está inactivo.");
                }
            }
        }
    }

    private void asegurarSinSaldoInicial(Connection connection, long clienteId) throws SQLException {
        if (tieneSaldoInicial(connection, clienteId)) {
            throw new ValidationException("Este cliente ya tiene un saldo inicial migrado.");
        }
    }

    private boolean tieneSaldoInicial(Connection connection, long clienteId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM cliente_saldo_inicial WHERE cliente_id = ? LIMIT 1")) {
            statement.setLong(1, clienteId);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private String combinarReferencia(String lote, String referenciaFila) {
        if (lote == null || lote.isBlank()) return referenciaFila;
        if (referenciaFila == null || referenciaFila.isBlank()) return lote;
        return lote + " · " + referenciaFila;
    }

    private void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private boolean esSaldoDuplicado(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("cliente_saldo_inicial.cliente_id")
                || message.contains("unique constraint failed: cliente_saldo_inicial");
    }

    private boolean esDocumentoDuplicado(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("cliente.documento")
                || message.contains("uq_cliente_documento");
    }
}
