package py.sistienda.core.repository;

import py.sistienda.core.model.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    List<Producto> findAllActive();

    default Optional<Producto> findByBarcode(String codigoBarras) {
        return Optional.empty();
    }

    default Optional<Producto> findByPlu(int plu) {
        return Optional.empty();
    }

    Producto create(Producto producto);

    Producto update(Producto producto);

    void deactivate(long productoId);
}
