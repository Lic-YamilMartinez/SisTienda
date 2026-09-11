package py.sistienda.core.model;

public record DevolucionResultado(
        long devolucionId,
        long ventaId,
        long nroTicket,
        double totalDevuelto,
        double costoRevertido,
        double gananciaRevertida
) {
}
