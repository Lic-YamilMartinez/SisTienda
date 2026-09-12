package py.sistienda.core.model;

public record ReposicionItem(
        Producto producto,
        double cantidadSugerida,
        double inversionEstimada
) {
    public boolean sinStock() {
        return producto.stockActual() <= 0.000001d;
    }
}
