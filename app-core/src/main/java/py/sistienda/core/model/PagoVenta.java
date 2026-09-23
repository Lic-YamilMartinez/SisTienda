package py.sistienda.core.model;

import py.sistienda.core.exception.ValidationException;

public record PagoVenta(
        MetodoPago metodoPago,
        double monto
) {
    public PagoVenta {
        if (metodoPago == null || metodoPago == MetodoPago.MIXTO) {
            throw new ValidationException("El componente de pago no es válido.");
        }
        if (!Double.isFinite(monto) || monto <= 0) {
            throw new ValidationException("El monto del componente de pago debe ser mayor a cero.");
        }
    }
}
