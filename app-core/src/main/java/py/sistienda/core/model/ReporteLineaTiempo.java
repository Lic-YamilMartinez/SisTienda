package py.sistienda.core.model;

import java.time.LocalDate;

public record ReporteLineaTiempo(
        LocalDate periodo,
        double ventas,
        double gananciaComercial,
        long tickets
) {
}
