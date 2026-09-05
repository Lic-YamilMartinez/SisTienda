package py.sistienda.core.model;

import java.time.LocalDateTime;

public record CompraResumen(
        long id,
        LocalDateTime fecha,
        String proveedor,
        String nroDocumento,
        double total,
        String usuario
) {
}
