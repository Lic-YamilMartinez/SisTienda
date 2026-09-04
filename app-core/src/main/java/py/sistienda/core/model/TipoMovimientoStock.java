package py.sistienda.core.model;

public enum TipoMovimientoStock {
    ENTRADA("Entrada"),
    SALIDA("Salida");

    private final String descripcion;

    TipoMovimientoStock(String descripcion) {
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
