package py.sistienda.core.repository;

import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.ResumenVentasCaja;

import java.util.Optional;

public interface CajaRepository {

    Optional<CajaSesion> findOpenByUser(long usuarioId);

    CajaSesion open(long usuarioId, double montoApertura, String notas);

    CajaSesion close(long cajaSesionId, double montoCierre, String notas);

    ResumenVentasCaja salesSummary(long cajaSesionId);
}
