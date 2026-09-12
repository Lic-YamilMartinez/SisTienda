package py.sistienda.core.model;

public record ImportacionProductoEntrada(
        int fila,
        String nombre,
        String categoria,
        UnidadMedida unidadMedida,
        double costo,
        double precioVenta,
        double stockInicial,
        double stockMinimo,
        double stockIdeal,
        String codigoBarras,
        Integer pluBalanza
) {
}
