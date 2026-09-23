package py.sistienda.core.model;

import java.util.List;

public record VentaResultado(
        long ventaId,
        int nroTicket,
        double total,
        double recibido,
        double vuelto,
        double gananciaTotal,
        MetodoPago metodoPago,
        List<PagoVenta> pagos
) {
    public VentaResultado {
        pagos = pagos == null ? List.of() : List.copyOf(pagos);
    }

    public VentaResultado(
            long ventaId,
            int nroTicket,
            double total,
            double recibido,
            double vuelto,
            double gananciaTotal,
            MetodoPago metodoPago
    ) {
        this(ventaId, nroTicket, total, recibido, vuelto, gananciaTotal, metodoPago, List.of());
    }

    public double monto(MetodoPago metodo) {
        return pagos.stream()
                .filter(pago -> pago.metodoPago() == metodo)
                .mapToDouble(PagoVenta::monto)
                .sum();
    }
}
