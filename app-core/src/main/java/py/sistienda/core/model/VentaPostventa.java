package py.sistienda.core.model;

import java.time.LocalDateTime;
import java.util.List;

public record VentaPostventa(
        long ventaId,
        long nroTicket,
        long cajaSesionId,
        LocalDateTime fecha,
        MetodoPago metodoPago,
        double totalOriginal,
        double gananciaOriginal,
        boolean anulada,
        String motivoAnulacion,
        double totalDevuelto,
        List<VentaPostventaLinea> lineas
) {
    public double totalNeto() {
        return anulada ? 0d : Math.max(0d, totalOriginal - totalDevuelto);
    }

    public boolean tieneDevoluciones() {
        return totalDevuelto > 0.000001d;
    }
}
