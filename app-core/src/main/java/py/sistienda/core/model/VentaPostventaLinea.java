package py.sistienda.core.model;

public record VentaPostventaLinea(
        long ventaDetalleId,
        long productoId,
        String producto,
        UnidadMedida unidadMedida,
        double cantidadVendida,
        double cantidadDevuelta,
        double precioUnitario,
        double costoUnitario
) {
    public double cantidadDisponible() {
        return Math.max(0d, cantidadVendida - cantidadDevuelta);
    }

    public double subtotalDisponible() {
        return cantidadDisponible() * precioUnitario;
    }

    public double gananciaUnitaria() {
        return precioUnitario - costoUnitario;
    }
}
