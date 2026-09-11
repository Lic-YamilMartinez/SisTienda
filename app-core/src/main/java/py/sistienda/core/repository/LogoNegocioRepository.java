package py.sistienda.core.repository;

import py.sistienda.core.model.LogoNegocio;

import java.util.Optional;

public interface LogoNegocioRepository {

    Optional<LogoNegocio> get();

    LogoNegocio save(LogoNegocio logo);

    void delete();
}
