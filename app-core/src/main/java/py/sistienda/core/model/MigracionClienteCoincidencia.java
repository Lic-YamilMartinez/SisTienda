package py.sistienda.core.model;

public record MigracionClienteCoincidencia(
        Long clienteId,
        boolean tieneSaldoInicial
) {
    public static MigracionClienteCoincidencia nueva() {
        return new MigracionClienteCoincidencia(null, false);
    }

    public boolean existeCliente() {
        return clienteId != null && clienteId > 0;
    }
}
