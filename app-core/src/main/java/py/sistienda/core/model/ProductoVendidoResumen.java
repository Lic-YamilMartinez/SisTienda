package py.sistienda.core.model;

public record ProductoVendidoResumen(
        long productoId,
        String producto,
        UnidadMedida unidadMedida,
        double cantidad,
        double ventas,
        double costo,
        double ganancia
) {
}
