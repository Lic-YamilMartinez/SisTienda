package py.sistienda.core.model;

public record ImportacionProductoResultado(
        int productosCreados,
        int categoriasCreadas,
        int movimientosStock
) {
}
