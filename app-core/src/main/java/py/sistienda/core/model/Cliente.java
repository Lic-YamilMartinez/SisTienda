package py.sistienda.core.model;

public record Cliente(
        long id,
        String nombre,
        String documento,
        String telefono,
        String direccion,
        String nota,
        boolean activo
) {
    @Override
    public String toString() {
        return nombre;
    }
}
