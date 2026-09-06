package py.sistienda.core.model;

import java.time.LocalDateTime;
import java.util.List;

public record ArqueoCajaDetalle(
        long cajaId,
        String usuario,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        EstadoCaja estado,
        double fondoInicial,
        ResumenVentasCaja ventas,
        ResumenMovimientosCaja movimientos,
        long tickets,
        double efectivoEsperado,
        Double efectivoContado,
        Double diferencia,
        String notas,
        List<MovimientoCaja> detalleMovimientos
) {
}
