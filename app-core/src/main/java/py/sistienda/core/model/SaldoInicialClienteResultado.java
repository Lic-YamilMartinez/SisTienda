package py.sistienda.core.model;

import java.time.LocalDate;

public record SaldoInicialClienteResultado(
        long id,
        long clienteId,
        double monto,
        LocalDate fechaReferencia,
        String referencia,
        String observacion
) {
}
