package py.sistienda.data.repository;

import py.sistienda.core.model.Empresa;
import py.sistienda.core.repository.EmpresaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.SQLException;
import java.util.Objects;

public final class SqliteEmpresaRepository implements EmpresaRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteEmpresaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public Empresa get() {
        String sql = "SELECT id, nombre, ruc, direccion, telefono, mensaje_ticket FROM empresa WHERE id = 1";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("No existe la configuración de empresa.");
            }
            return map(result);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar la configuración de la tienda.", e);
        }
    }

    @Override
    public Empresa save(Empresa empresa) {
        String sql = """
                UPDATE empresa
                SET nombre = ?, ruc = ?, direccion = ?, telefono = ?, mensaje_ticket = ?
                WHERE id = 1
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, empresa.nombre());
            statement.setString(2, empresa.ruc());
            statement.setString(3, empresa.direccion());
            statement.setString(4, empresa.telefono());
            statement.setString(5, empresa.mensajeTicket());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se pudo actualizar la empresa.");
            }
            return get();
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo guardar la configuración de la tienda.", e);
        }
    }

    private Empresa map(java.sql.ResultSet result) throws SQLException {
        return new Empresa(
                result.getLong("id"),
                result.getString("nombre"),
                result.getString("ruc"),
                result.getString("direccion"),
                result.getString("telefono"),
                result.getString("mensaje_ticket")
        );
    }
}
