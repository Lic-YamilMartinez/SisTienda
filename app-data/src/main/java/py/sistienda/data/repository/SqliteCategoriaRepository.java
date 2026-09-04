package py.sistienda.data.repository;

import py.sistienda.core.model.CategoriaProducto;
import py.sistienda.core.repository.CategoriaRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
                ORDER BY nombre
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
            throw new RuntimeException("Error listando categorias activas", e);
        }
    }
}
