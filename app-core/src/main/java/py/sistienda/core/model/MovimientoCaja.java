package py.sistienda.core.model;

import java.time.LocalDateTime;

public record MovimientoCaja(
        long id,
        long cajaSesionId,
        long usuarioId,
        String usuario,
        LocalDateTime fecha,
        TipoMovimientoCaja tipo,
        String categoria,
        String concepto,
        double monto,
        String referencia
) {
}
