package py.sistienda.core.repository;

import py.sistienda.core.model.InventarioConteoDetalle;
import py.sistienda.core.model.InventarioConteoItem;
import py.sistienda.core.model.InventarioConteoResultado;
import py.sistienda.core.model.InventarioConteoResumen;

import java.util.List;

public interface InventarioRepository {
    InventarioConteoResultado registrar(
            long usuarioId,
            String motivo,
            String observacion,
            List<InventarioConteoItem> items
    );

    List<InventarioConteoResumen> recientes(int limite);

    List<InventarioConteoDetalle> detalle(long inventarioId);
}
