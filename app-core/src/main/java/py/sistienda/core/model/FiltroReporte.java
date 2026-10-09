package py.sistienda.core.model;

import java.time.LocalDate;

public record FiltroReporte(
        LocalDate desde,
        LocalDate hasta,
        GranularidadReporte granularidad,
        MetodoPago metodoPago,
        Long clienteId,
        Long productoId,
        TipoVentaReporte tipoVenta
) {
    public FiltroReporte {
        granularidad = granularidad == null ? GranularidadReporte.AUTO : granularidad;
        tipoVenta = tipoVenta == null ? TipoVentaReporte.TODOS : tipoVenta;
    }

    public static FiltroReporte basico(LocalDate desde, LocalDate hasta, MetodoPago metodoPago) {
        return new FiltroReporte(
                desde, hasta, GranularidadReporte.AUTO,
                metodoPago, null, null, TipoVentaReporte.TODOS
        );
    }

    public boolean tieneFiltroNegocio() {
        return metodoPago != null
                || clienteId != null
                || productoId != null
                || tipoVenta != TipoVentaReporte.TODOS;
    }
}
