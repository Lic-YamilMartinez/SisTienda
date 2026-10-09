package py.sistienda.core.model;

import java.time.LocalDate;

public record ReporteLineaTiempo(
        LocalDate periodo,
        double ventas,
        double costoMercaderia,
        double gananciaComercial,
        long tickets
) {
    public ReporteLineaTiempo(LocalDate periodo, double ventas, double gananciaComercial, long tickets) {
        this(periodo, ventas, ventas - gananciaComercial, gananciaComercial, tickets);
    }

    public double margenPorcentaje() {
        return Math.abs(ventas) <= 0.000001d ? 0d : (gananciaComercial / ventas) * 100d;
    }
}
