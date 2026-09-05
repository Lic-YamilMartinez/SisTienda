package py.sistienda.core.repository;

import py.sistienda.core.model.Empresa;

public interface EmpresaRepository {

    Empresa get();

    Empresa save(Empresa empresa);
}
