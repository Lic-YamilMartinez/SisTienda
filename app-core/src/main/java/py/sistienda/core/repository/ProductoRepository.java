package py.sistienda.core.repository;

import py.sistienda.core.model.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    List<Producto> findAllActive();

    Optional<Producto> findByBarcode(String codigoBarras);

    Optional<Producto> findByPlu(int plu);

    Producto create(Producto producto);

    Producto update(Producto producto);

    void deactivate(long productoId);
}
