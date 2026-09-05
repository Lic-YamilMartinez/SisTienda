package py.sistienda.core.model;

public record ResumenVentasCaja(
        double efectivo,
        double transferencia,
        double tarjeta,
        double total
) {
    public static ResumenVentasCaja vacio() {
        return new ResumenVentasCaja(0, 0, 0, 0);
    }
}
