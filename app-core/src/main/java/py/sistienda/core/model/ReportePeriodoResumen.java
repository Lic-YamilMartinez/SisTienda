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
        double fiado,
        double otrosIngresos,
        double egresosOperativos,
        double resultadoNetoOperativo
) {
    public ReportePeriodoResumen(
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
        this(desde, hasta, metodoPago, ventas, costoMercaderia, gananciaComercial, tickets,
                ticketPromedio, efectivo, transferencia, tarjeta, 0d, otrosIngresos,
                egresosOperativos, resultadoNetoOperativo);
    }
}
