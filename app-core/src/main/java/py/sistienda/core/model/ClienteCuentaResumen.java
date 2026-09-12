package py.sistienda.core.model;

import java.time.LocalDateTime;

public record ClienteCuentaResumen(
        Cliente cliente,
        double saldo,
        LocalDateTime ultimoMovimiento
) {
    public boolean tieneDeuda() {
        return saldo > 0.000001d;
    }

    public boolean tieneSaldoFavor() {
        return saldo < -0.000001d;
    }
}
