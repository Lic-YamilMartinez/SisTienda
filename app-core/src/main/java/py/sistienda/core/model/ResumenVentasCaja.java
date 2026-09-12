package py.sistienda.core.model;

public record ResumenVentasCaja(
        double efectivo,
        double transferencia,
        double tarjeta,
        double fiado,
        double total
) {
    public ResumenVentasCaja(double efectivo, double transferencia, double tarjeta, double total) {
        this(efectivo, transferencia, tarjeta, 0d, total);
    }

    public static ResumenVentasCaja vacio() {
        return new ResumenVentasCaja(0, 0, 0, 0, 0);
    }
}
