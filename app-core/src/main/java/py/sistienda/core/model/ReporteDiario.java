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
        double tarjeta
) {
}
