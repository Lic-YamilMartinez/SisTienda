package py.sistienda.core.model;

import py.sistienda.core.util.MoneyMath;

public record LineaCompra(
        Producto producto,
        double cantidad,
        double costoUnitario
) {
    public double subtotal() {
        return MoneyMath.subtotal(costoUnitario, cantidad);
    }
}
