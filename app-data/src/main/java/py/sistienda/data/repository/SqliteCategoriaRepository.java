package py.sistienda.data.repository;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CategoriaProducto;
import py.sistienda.core.repository.CategoriaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteCategoriaRepository implements CategoriaRepository {

    private final SqliteConnectionFactory connectionFactory;

    public SqliteCategoriaRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public List<CategoriaProducto> findAllActive() {
        String sql = """
                SELECT id, nombre, activo
                FROM categoria_producto
                WHERE activo = 1
                ORDER BY nombre COLLATE NOCASE
                """;

        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            List<CategoriaProducto> categorias = new ArrayList<>();
            while (resultSet.next()) {
                categorias.add(new CategoriaProducto(
                        resultSet.getLong("id"),
                        resultSet.getString("nombre"),
                        resultSet.getInt("activo") == 1
                ));
            }
            return categorias;
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron cargar las categorías.", e);
        }
    }

    @Override
    public CategoriaProducto create(String nombre) {
        String sql = "INSERT INTO categoria_producto (nombre, activo) VALUES (?, 1)";

        try (var connection = connectionFactory.open();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nombre);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new CategoriaProducto(keys.getLong(1), nombre, true);
                }
            }
            throw new IllegalStateException("SQLite no devolvió el id de la categoría creada.");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                throw new ValidationException("Ya existe una categoría con ese nombre.");
            }
            throw new RuntimeException("No se pudo crear la categoría.", e);
        }
    }
}
