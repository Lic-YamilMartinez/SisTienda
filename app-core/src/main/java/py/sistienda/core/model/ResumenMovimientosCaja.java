package py.sistienda.core.model;

public record ResumenMovimientosCaja(double ingresos, double egresos) {
    public static ResumenMovimientosCaja vacio() {
        return new ResumenMovimientosCaja(0d, 0d);
    }
}
