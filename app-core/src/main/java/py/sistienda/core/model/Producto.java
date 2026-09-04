package py.sistienda.core.model;

public record Producto(
        long id,
        String nombre,
        Long categoriaId,
        String categoriaNombre,
        UnidadMedida unidadMedida,
        double precioVenta,
        double costo,
        double stockActual,
        boolean activo
) {
}
