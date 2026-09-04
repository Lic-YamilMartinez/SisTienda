package py.sistienda.core.model;

public enum UnidadMedida {
    UN("Unidad"),
    KG("Kilogramo");

    private final String descripcion;

    UnidadMedida(String descripcion) {
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
