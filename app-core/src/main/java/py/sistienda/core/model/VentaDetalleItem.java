package py.sistienda.core.model;

public record VentaDetalleItem(
        String producto,
        double cantidad,
        double precioUnitario,
        double subtotal
) {
}
