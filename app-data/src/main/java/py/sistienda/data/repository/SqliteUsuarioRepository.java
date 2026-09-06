package py.sistienda.data.repository;

import py.sistienda.core.model.RolUsuario;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.UsuarioRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class SqliteUsuarioRepository implements UsuarioRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteUsuarioRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public boolean existsActiveUser() {
        String sql = "SELECT 1 FROM usuario WHERE activo = 1 LIMIT 1";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            return result.next();
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo verificar si existe un usuario activo.", e);
        }
    }

    @Override
    public Optional<UsuarioCredential> findActiveByUsername(String username) {
        String sql = "SELECT id, username, password_hash, rol, activo FROM usuario WHERE username = ? AND activo = 1 LIMIT 1";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new UsuarioCredential(
                        result.getLong("id"),
                        result.getString("username"),
                        result.getString("password_hash"),
                        result.getString("rol"),
                        result.getInt("activo") == 1
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el usuario.", e);
        }
    }

    @Override
    public Usuario createOwner(String username, String passwordHash) {
        return createInternal(username, passwordHash, RolUsuario.DUENIO);
    }

    @Override
    public List<Usuario> findAll() {
        String sql = """
                SELECT id, username, rol, activo
                FROM usuario
                ORDER BY activo DESC,
                         CASE rol
                           WHEN 'DUENIO' THEN 1
                           WHEN 'ADMINISTRADOR' THEN 2
                           WHEN 'CAJERO' THEN 3
                           ELSE 4
                         END,
                         username COLLATE NOCASE
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            List<Usuario> usuarios = new ArrayList<>();
            while (result.next()) usuarios.add(map(result));
            return usuarios;
        } catch (SQLException e) {
            throw new RuntimeException("No se pudieron cargar los usuarios.", e);
        }
    }

    @Override
    public Optional<Usuario> findById(long id) {
        String sql = "SELECT id, username, rol, activo FROM usuario WHERE id = ? LIMIT 1";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo consultar el usuario.", e);
        }
    }

    @Override
    public Usuario create(String username, String passwordHash, RolUsuario rol) {
        return createInternal(username, passwordHash, rol);
    }

    @Override
    public Usuario updateRole(long id, RolUsuario rol) {
        String sql = "UPDATE usuario SET rol = ? WHERE id = ?";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, rol.name());
            statement.setLong(2, id);
            if (statement.executeUpdate() == 0) {
                throw new RuntimeException("El usuario ya no existe.");
            }
            return findById(id).orElseThrow(() -> new RuntimeException("El usuario ya no existe."));
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo actualizar el rol del usuario.", e);
        }
    }

    @Override
    public Usuario setActive(long id, boolean activo) {
        String sql = "UPDATE usuario SET activo = ? WHERE id = ?";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, activo ? 1 : 0);
            statement.setLong(2, id);
            if (statement.executeUpdate() == 0) {
                throw new RuntimeException("El usuario ya no existe.");
            }
            return findById(id).orElseThrow(() -> new RuntimeException("El usuario ya no existe."));
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo actualizar el estado del usuario.", e);
        }
    }

    @Override
    public void updatePassword(long id, String passwordHash) {
        String sql = "UPDATE usuario SET password_hash = ? WHERE id = ?";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, id);
            if (statement.executeUpdate() == 0) {
                throw new RuntimeException("El usuario ya no existe.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo actualizar la contraseña.", e);
        }
    }

    @Override
    public long countActiveByRole(RolUsuario rol) {
        String sql = "SELECT COUNT(*) FROM usuario WHERE activo = 1 AND rol = ?";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, rol.name());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo contar los usuarios activos.", e);
        }
    }

    private Usuario createInternal(String username, String passwordHash, RolUsuario rol) {
        String sql = "INSERT INTO usuario (username, password_hash, rol, activo) VALUES (?, ?, ?, 1)";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, rol.name());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite no devolvió el id del usuario creado.");
                return new Usuario(keys.getLong(1), username, rol.name(), true);
            }
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("usuario.username") || message.contains("unique")) {
                throw new RuntimeException("Ese nombre de usuario ya está en uso.");
            }
            throw new RuntimeException("No se pudo crear el usuario.", e);
        }
    }

    private Usuario map(java.sql.ResultSet result) throws SQLException {
        return new Usuario(
                result.getLong("id"),
                result.getString("username"),
                result.getString("rol"),
                result.getInt("activo") == 1
        );
    }
}
