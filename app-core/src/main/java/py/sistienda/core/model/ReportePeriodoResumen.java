package py.sistienda.core.model;

import java.time.LocalDate;

public record ReportePeriodoResumen(
        LocalDate desde,
        LocalDate hasta,
        MetodoPago metodoPago,
        double ventas,
        double costoMercaderia,
        double gananciaComercial,
        long tickets,
        double ticketPromedio,
        double efectivo,
        double transferencia,
        double tarjeta,
        double otrosIngresos,
        double egresosOperativos,
        double resultadoNetoOperativo
) {
}
