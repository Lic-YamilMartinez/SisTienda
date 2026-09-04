package py.sistienda.core.repository;

import py.sistienda.core.model.CategoriaProducto;

import java.util.List;

public interface CategoriaRepository {
    List<CategoriaProducto> findAllActive();
}
