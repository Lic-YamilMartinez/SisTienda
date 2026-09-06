package py.sistienda.core.model;

public record Usuario(
        long id,
        String username,
        String rol,
        boolean activo
) {
    public RolUsuario rolUsuario() {
        return RolUsuario.desde(rol);
    }

    public boolean esDueno() {
        return rolUsuario() == RolUsuario.DUENIO;
    }
}
