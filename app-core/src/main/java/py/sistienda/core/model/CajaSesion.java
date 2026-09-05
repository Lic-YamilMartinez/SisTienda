package py.sistienda.core.model;

import java.time.LocalDateTime;

public record CajaSesion(
        long id,
        long usuarioId,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        double montoApertura,
        Double montoCierre,
        EstadoCaja estado,
        String notas
) {
    public boolean abierta() {
        return estado == EstadoCaja.ABIERTA;
    }
}
