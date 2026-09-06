package py.sistienda.core.repository;

import py.sistienda.core.model.ArqueoCajaDetalle;
import py.sistienda.core.model.ArqueoCajaResumen;

import java.util.List;

public interface ArqueoCajaRepository {
    List<ArqueoCajaResumen> findRecent(int limit);

    ArqueoCajaDetalle findDetail(long cajaId);
}
