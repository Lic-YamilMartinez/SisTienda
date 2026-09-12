package py.sistienda.core.model;

import java.time.LocalDateTime;

public record InventarioConteoResultado(
        long id,
        LocalDateTime fecha,
        String usuario,
        String motivo,
        int productosContados,
        int productosAjustados
) {
}
