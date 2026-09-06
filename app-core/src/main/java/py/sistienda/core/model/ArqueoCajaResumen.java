package py.sistienda.core.model;

import java.time.LocalDateTime;

public record ArqueoCajaResumen(
        long cajaId,
        String usuario,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        EstadoCaja estado,
        double fondoInicial,
        double totalVendido,
        long tickets,
        double efectivoEsperado,
        Double efectivoContado,
        Double diferencia
) {
    public boolean cerrada() {
        return estado == EstadoCaja.CERRADA;
    }
}
