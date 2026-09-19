package py.sistienda.core.repository;

import py.sistienda.core.model.TipoMovimientoStock;

public interface MovimientoStockRepository {
    default void register(
            long productoId,
            long usuarioId,
            py.sistienda.core.model.TipoMovimientoStock tipo,
            String motivo,
            double cantidad,
            String referencia,
            String observacion
    ) {
        register(productoId, tipo, motivo, cantidad, referencia, observacion);
    }

    void register(
            long productoId,
            TipoMovimientoStock tipo,
            String motivo,
            double cantidad,
            String referencia,
            String observacion
    );
}
