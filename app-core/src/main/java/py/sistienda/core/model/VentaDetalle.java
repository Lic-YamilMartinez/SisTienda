package py.sistienda.core.model;

import java.time.LocalDateTime;
import java.util.List;

public record VentaDetalle(
        long id,
        long nroTicket,
        LocalDateTime fecha,
        String usuario,
        MetodoPago metodoPago,
        double total,
        double recibido,
        double vuelto,
        double ganancia,
        boolean anulada,
        List<VentaDetalleItem> items
) {
    public VentaDetalle {
        items = List.copyOf(items);
    }
}
