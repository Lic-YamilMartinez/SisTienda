package py.sistienda.core.model;

public record ResumenVentasCaja(
        double efectivo,
        double transferencia,
        double tarjeta,
        double fiado,
        double total,
        double ganancia
) {
    public ResumenVentasCaja(double efectivo, double transferencia, double tarjeta, double total) {
        this(efectivo, transferencia, tarjeta, 0d, total, 0d);
    }

    public ResumenVentasCaja(double efectivo, double transferencia, double tarjeta, double fiado, double total) {
        this(efectivo, transferencia, tarjeta, fiado, total, 0d);
    }

    public static ResumenVentasCaja vacio() {
        return new ResumenVentasCaja(0, 0, 0, 0, 0, 0);
    }
}
