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
        String cliente,
        List<VentaDetalleItem> items,
        List<PagoVenta> pagos
) {
    public VentaDetalle {
        items = List.copyOf(items);
        pagos = pagos == null ? List.of() : List.copyOf(pagos);
    }

    public VentaDetalle(
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
            String cliente,
            List<VentaDetalleItem> items
    ) {
        this(id, nroTicket, fecha, usuario, metodoPago, total, recibido, vuelto, ganancia,
                anulada, cliente, items, List.of());
    }

    public VentaDetalle(
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
        this(id, nroTicket, fecha, usuario, metodoPago, total, recibido, vuelto, ganancia,
                anulada, null, items, List.of());
    }

    public double monto(MetodoPago metodo) {
        return pagos.stream()
                .filter(pago -> pago.metodoPago() == metodo)
                .mapToDouble(PagoVenta::monto)
                .sum();
    }
}
