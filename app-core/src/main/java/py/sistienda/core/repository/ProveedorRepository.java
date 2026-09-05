package py.sistienda.core.repository;

import py.sistienda.core.model.Proveedor;

import java.util.List;

public interface ProveedorRepository {
    List<Proveedor> findAllActive();
    Proveedor create(Proveedor proveedor);
    Proveedor update(Proveedor proveedor);
    void deactivate(long proveedorId);
}
