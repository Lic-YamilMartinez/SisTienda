package py.sistienda.core.model;

import java.time.LocalDateTime;

public record ClienteCuentaMovimiento(
        LocalDateTime fecha,
        String tipo,
        String referencia,
        double cargo,
        double abono,
        String detalle,
        String medioPago
) {
}
