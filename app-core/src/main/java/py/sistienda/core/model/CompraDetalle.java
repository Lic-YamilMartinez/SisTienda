package py.sistienda.core.model;

import java.time.LocalDateTime;
import java.util.List;

public record CompraDetalle(
        long id,
        LocalDateTime fecha,
        Proveedor proveedor,
        String nroDocumento,
        double total,
        String usuario,
        String observacion,
        List<CompraDetalleItem> items
) {
}
