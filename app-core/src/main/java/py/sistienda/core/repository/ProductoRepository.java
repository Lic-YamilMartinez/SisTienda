package py.sistienda.core.repository;

import py.sistienda.core.model.Producto;

import java.util.List;

public interface ProductoRepository {
    List<Producto> findAllActive();

    Producto create(Producto producto);

    Producto update(Producto producto);

    void deactivate(long productoId);
}
