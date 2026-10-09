package py.sistienda.core.model;

import java.time.LocalDate;

public record ImportacionClienteSaldoEntrada(
        int fila,
        String nombre,
        String documento,
        String telefono,
        String direccion,
        double saldoInicial,
        LocalDate fechaReferencia,
        String referencia,
        String observacion,
        Long clienteExistenteId
) {
    public boolean clienteExistente() {
        return clienteExistenteId != null && clienteExistenteId > 0;
    }
}
