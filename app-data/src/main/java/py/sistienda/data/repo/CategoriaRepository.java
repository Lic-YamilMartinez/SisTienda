package py.sistienda.data.repo;

import py.sistienda.core.model.CategoriaProducto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class CategoriaRepository {

    private final Connection con;

    public CategoriaRepository(Connection con) {
        this.con = con;
    }

    public List<CategoriaProducto> findAll() {
        String sql = """
                SELECT id, nombre, activo
                FROM categoria_producto
                WHERE activo = 1
                ORDER BY nombre
                """;

        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<CategoriaProducto> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new CategoriaProducto(
                        rs.getLong("id"),
                        rs.getString("nombre"),
                        rs.getInt("activo") == 1
                ));
            }
            return out;

        } catch (Exception e) {
            throw new RuntimeException("Error listando categorias", e);
        }
    }
}
