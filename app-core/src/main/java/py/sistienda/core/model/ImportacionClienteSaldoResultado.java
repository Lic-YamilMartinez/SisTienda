package py.sistienda.core.model;

public record ImportacionClienteSaldoResultado(
        int clientesCreados,
        int clientesExistentes,
        int saldosInicialesCargados,
        double totalMigrado
) {
}
