package py.sistienda.core.model;

import java.time.LocalDateTime;

public record ClienteCuentaMovimiento(
        LocalDateTime fecha,
        String tipo,
        String referencia,
        double cargo,
        double abono,
        String detalle,
        String medioPago,
        Long ventaId
) {
    public ClienteCuentaMovimiento(
            LocalDateTime fecha,
            String tipo,
            String referencia,
            double cargo,
            double abono,
            String detalle,
            String medioPago
    ) {
        this(fecha, tipo, referencia, cargo, abono, detalle, medioPago, null);
    }

    public boolean tieneVenta() {
        return ventaId != null && ventaId > 0;
    }
}
