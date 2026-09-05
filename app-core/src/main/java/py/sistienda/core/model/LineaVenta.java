package py.sistienda.core.model;

import java.util.Objects;

public record LineaVenta(
        Producto producto,
        double cantidad
) {
    public LineaVenta {
        Objects.requireNonNull(producto);
    }

    public double subtotal() {
        return producto.precioVenta() * cantidad;
    }

    public double ganancia() {
        return (producto.precioVenta() - producto.costo()) * cantidad;
    }
}
