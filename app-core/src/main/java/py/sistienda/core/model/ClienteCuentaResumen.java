package py.sistienda.core.model;

import java.time.LocalDateTime;

public record ClienteCuentaResumen(
        Cliente cliente,
        double saldo,
        LocalDateTime ultimoMovimiento,
        double saldoInicial,
        double saldoInicialPendiente
) {
    public ClienteCuentaResumen(Cliente cliente, double saldo, LocalDateTime ultimoMovimiento) {
        this(cliente, saldo, ultimoMovimiento, 0d, 0d);
    }

    public boolean tieneDeuda() {
        return saldo > 0.000001d;
    }

    public boolean tieneSaldoFavor() {
        return saldo < -0.000001d;
    }

    public boolean tieneSaldoInicial() {
        return saldoInicial > 0.000001d;
    }

    public double saldoSystiendaPendiente() {
        return Math.max(0d, saldo - Math.max(0d, saldoInicialPendiente));
    }
}
