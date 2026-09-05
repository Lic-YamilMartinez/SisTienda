package py.sistienda.core.model;

public record Proveedor(
        long id,
        String nombre,
        String ruc,
        String telefono,
        String email,
        String direccion,
        boolean activo
) {
}
