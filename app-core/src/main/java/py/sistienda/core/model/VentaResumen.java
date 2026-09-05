package py.sistienda.core.model;

import java.time.LocalDateTime;

public record VentaResumen(
        long id,
        long nroTicket,
        LocalDateTime fecha,
        String usuario,
        MetodoPago metodoPago,
        double total,
        double ganancia,
        boolean anulada
) {
}
