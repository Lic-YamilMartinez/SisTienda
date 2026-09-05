package py.sistienda.core.model;

public record Usuario(
        long id,
        String username,
        String rol,
        boolean activo
) {
}
