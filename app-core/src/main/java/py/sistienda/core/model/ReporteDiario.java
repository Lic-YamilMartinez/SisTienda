package py.sistienda.core.model;

import java.time.LocalDate;

public record ReporteDiario(
        LocalDate fecha,
        double ventas,
        double ganancia,
        long tickets,
        double ticketPromedio,
        double efectivo,
        double transferencia,
        double tarjeta,
        double fiado
) {
    public ReporteDiario(LocalDate fecha, double ventas, double ganancia, long tickets,
                         double ticketPromedio, double efectivo, double transferencia, double tarjeta) {
        this(fecha, ventas, ganancia, tickets, ticketPromedio, efectivo, transferencia, tarjeta, 0d);
    }
}
