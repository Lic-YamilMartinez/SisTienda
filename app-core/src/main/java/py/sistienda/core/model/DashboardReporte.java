package py.sistienda.core.model;

import java.util.List;

public record DashboardReporte(
        ReportePeriodoResumen resumen,
        List<ReporteLineaTiempo> lineaTiempo,
        List<ProductoVendidoResumen> productosMasVendidos,
        List<VentaResumen> ventas
) {
    public DashboardReporte {
        lineaTiempo = List.copyOf(lineaTiempo);
        productosMasVendidos = List.copyOf(productosMasVendidos);
        ventas = List.copyOf(ventas);
    }
}
