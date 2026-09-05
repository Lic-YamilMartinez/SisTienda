package py.sistienda.core.model;

public record ControlEfectivoCaja(
        double fondoInicial,
        double ventasEfectivo,
        double ingresos,
        double egresos,
        double efectivoEsperado
) {
}
