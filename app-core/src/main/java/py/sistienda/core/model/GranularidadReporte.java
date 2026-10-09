package py.sistienda.core.model;

public enum GranularidadReporte {
    AUTO("Automático"),
    DIARIO("Diario"),
    MENSUAL("Mensual");

    private final String descripcion;

    GranularidadReporte(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
