package py.sistienda.core.model;

public record CompraDetalleItem(
        String producto,
        double cantidad,
        double costoUnitario,
        double subtotal
) {
}
