package py.sistienda.data.repository;

import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.UsuarioRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.SQLException;
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
                if (!result.next()) {
                    return Optional.empty();
                }
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
        String sql = "INSERT INTO usuario (username, password_hash, rol, activo) VALUES (?, ?, 'DUENIO', 1)";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.executeUpdate();

            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite no devolvió el id del usuario creado.");
                }
                return new Usuario(keys.getLong(1), username, "DUENIO", true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo crear el usuario dueño.", e);
        }
    }
}
