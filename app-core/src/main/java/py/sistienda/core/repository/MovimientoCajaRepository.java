package py.sistienda.core.repository;

import py.sistienda.core.model.MovimientoCaja;
import py.sistienda.core.model.ResumenMovimientosCaja;
import py.sistienda.core.model.TipoMovimientoCaja;

import java.util.List;

public interface MovimientoCajaRepository {
    MovimientoCaja create(long cajaSesionId, long usuarioId, TipoMovimientoCaja tipo,
                          String categoria, String concepto, double monto, String referencia);

    List<MovimientoCaja> findByCaja(long cajaSesionId);

    ResumenMovimientosCaja summary(long cajaSesionId);
}
