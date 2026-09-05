package py.sistienda.core.model;

public record Empresa(
        long id,
        String nombre,
        String ruc,
        String direccion,
        String telefono,
        String mensajeTicket
) {
}
