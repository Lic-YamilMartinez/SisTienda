package py.sistienda.core.repository;

import py.sistienda.core.model.TipoMovimientoStock;

public interface MovimientoStockRepository {
    void register(
            long productoId,
            TipoMovimientoStock tipo,
            String motivo,
            double cantidad,
            String referencia,
            String observacion
    );
}
