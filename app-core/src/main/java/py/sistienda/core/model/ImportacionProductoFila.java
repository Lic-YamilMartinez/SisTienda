package py.sistienda.core.model;

public record ImportacionProductoFila(
        int fila,
        String nombre,
        String categoria,
        String unidad,
        String costo,
        String precioVenta,
        String stockInicial,
        String stockMinimo,
        String stockIdeal,
        String codigoBarras,
        String pluBalanza
) {
}
