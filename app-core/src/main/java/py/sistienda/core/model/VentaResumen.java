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
        boolean anulada
) {
    public double totalNeto() {
        return anulada ? 0d : Math.max(0d, total - devuelto);
    }

    public boolean tieneDevolucion() {
        return devuelto > 0.000001d;
    }

    public boolean devueltaCompleta() {
        return tieneDevolucion() && totalNeto() <= 0.000001d;
    }
}
