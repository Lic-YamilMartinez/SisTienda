package py.sistienda.core.repository;

import py.sistienda.core.model.ConfiguracionPos;

public interface ConfiguracionPosRepository {
    ConfiguracionPos get();
    ConfiguracionPos save(ConfiguracionPos configuracion);
}
