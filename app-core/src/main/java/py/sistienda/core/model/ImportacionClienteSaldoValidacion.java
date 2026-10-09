package py.sistienda.core.model;

import java.util.List;

public record ImportacionClienteSaldoValidacion(
        int fila,
        String cliente,
        ImportacionClienteSaldoEntrada entrada,
        List<String> errores
) {
    public ImportacionClienteSaldoValidacion {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public boolean valida() {
        return entrada != null && errores.isEmpty();
    }

    public String resumenErrores() {
        if (valida()) {
            return entrada.clienteExistente()
                    ? "OK · se usará el cliente existente"
                    : "OK · se creará el cliente";
        }
        return errores.isEmpty() ? "Revisar fila" : String.join(" · ", errores);
    }
}
