package py.sistienda.core.model;

import java.util.List;

public record ImportacionProductoValidacion(
        int fila,
        String nombre,
        ImportacionProductoEntrada entrada,
        List<String> errores
) {
    public boolean valida() {
        return errores == null || errores.isEmpty();
    }

    public String resumenErrores() {
        return valida() ? "Lista para importar" : String.join(" · ", errores);
    }
}
