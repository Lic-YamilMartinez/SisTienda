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
        double devuelto,
        boolean anulada,
        String cliente
) {
    public VentaResumen(
            long id,
            long nroTicket,
            LocalDateTime fecha,
            String usuario,
            MetodoPago metodoPago,
            double total,
            double ganancia,
            double devuelto,
            boolean anulada
    ) {
        this(id, nroTicket, fecha, usuario, metodoPago, total, ganancia, devuelto, anulada, null);
    }

    public double totalNeto() {
        return anulada ? 0d : Math.max(0d, total - devuelto);
    }

    public boolean tieneDevolucion() {
        return devuelto > 0.000001d;
    }

    public boolean devueltaCompleta() {
        return tieneDevolucion() && totalNeto() <= 0.000001d;
    }

    public String clienteDisplay() {
        return cliente == null || cliente.isBlank() ? "Consumidor final" : cliente;
    }
}
