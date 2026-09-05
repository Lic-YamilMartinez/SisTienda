package py.sistienda.core.model;

public record VentaResultado(
        long ventaId,
        int nroTicket,
        double total,
        double recibido,
        double vuelto,
        double gananciaTotal,
        MetodoPago metodoPago
) {
}
