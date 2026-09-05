package py.sistienda.core.model;

public record LineaCompra(
        Producto producto,
        double cantidad,
        double costoUnitario
) {
    public double subtotal() {
        return cantidad * costoUnitario;
    }
}
