package py.sistienda.core.model;

public record ImportacionClienteSaldoFila(
        int fila,
        String nombre,
        String documento,
        String telefono,
        String direccion,
        String saldoInicial,
        String fechaReferencia,
        String referencia,
        String observacion
) {
}
