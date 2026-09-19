package py.sistienda.core.model;

import py.sistienda.core.util.MoneyMath;

import java.util.Objects;

public record LineaVenta(
        Producto producto,
        double cantidad
) {
    public LineaVenta {
        Objects.requireNonNull(producto);
    }

    public double subtotal() {
        return MoneyMath.subtotal(producto.precioVenta(), cantidad);
    }

    public double ganancia() {
        return subtotal() - MoneyMath.subtotal(producto.costo(), cantidad);
    }
}
