package py.sistienda.data.repository;

import py.sistienda.core.model.Proveedor;
import py.sistienda.core.repository.ProveedorRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteProveedorRepository implements ProveedorRepository {
    private final SqliteConnectionFactory connectionFactory;

    public SqliteProveedorRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public List<Proveedor> findAllActive() {
        String sql = """
                SELECT id, nombre, ruc, telefono, email, direccion, activo
                FROM proveedor
                WHERE activo = 1
                ORDER BY nombre COLLATE NOCASE
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            List<Proveedor> proveedores = new ArrayList<>();
            while (result.next()) {
                proveedores.add(map(result));
            }
            return List.copyOf(proveedores);
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron cargar los proveedores.", e);
        }
    }

    @Override
    public Proveedor create(Proveedor proveedor) {
        String sql = """
                INSERT INTO proveedor (nombre, ruc, telefono, email, direccion, activo)
                VALUES (?, ?, ?, ?, ?, 1)
                """;
        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, proveedor);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Proveedor(keys.getLong(1), proveedor.nombre(), proveedor.ruc(), proveedor.telefono(),
                            proveedor.email(), proveedor.direccion(), true);
                }
            }
            throw new IllegalStateException("SQLite no devolvió el id del proveedor.");
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear el proveedor.", e);
        }
    }

    @Override
    public Proveedor update(Proveedor proveedor) {
        String sql = """
                UPDATE proveedor
                SET nombre = ?, ruc = ?, telefono = ?, email = ?, direccion = ?, actualizado_en = datetime('now')
                WHERE id = ? AND activo = 1
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            bind(statement, proveedor);
            statement.setLong(6, proveedor.id());
            if (statement.executeUpdate() == 0) {
                throw new IllegalStateException("El proveedor ya no está disponible para editar.");
            }
            return proveedor;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo actualizar el proveedor.", e);
        }
    }

    @Override
    public void deactivate(long proveedorId) {
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(
                     "UPDATE proveedor SET activo = 0, actualizado_en = datetime('now') WHERE id = ? AND activo = 1")) {
            statement.setLong(1, proveedorId);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo desactivar el proveedor.", e);
        }
    }

    private void bind(PreparedStatement statement, Proveedor proveedor) throws Exception {
        statement.setString(1, proveedor.nombre());
        statement.setString(2, proveedor.ruc());
        statement.setString(3, proveedor.telefono());
        statement.setString(4, proveedor.email());
        statement.setString(5, proveedor.direccion());
    }

    private Proveedor map(ResultSet result) throws Exception {
        return new Proveedor(
                result.getLong("id"),
                result.getString("nombre"),
                result.getString("ruc"),
                result.getString("telefono"),
                result.getString("email"),
                result.getString("direccion"),
                result.getInt("activo") == 1
        );
    }
}
