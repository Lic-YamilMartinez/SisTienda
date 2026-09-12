package py.sistienda.core.model;

public record AbonoClienteResultado(
        long id,
        double monto,
        double saldoAnterior,
        double saldoNuevo,
        MetodoPago metodoPago
) {
}
