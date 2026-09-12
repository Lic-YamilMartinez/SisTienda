package py.sistienda.core.model;

import java.time.LocalDateTime;

public record InventarioConteoResumen(
        long id,
        LocalDateTime fecha,
        String usuario,
        String motivo,
        String observacion,
        int productosContados,
        int productosAjustados
) {
}
