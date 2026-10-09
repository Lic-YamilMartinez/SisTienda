package py.sistienda.core.model;

public enum TipoVentaReporte {
    TODOS("Todas"),
    CONTADO("Contado"),
    CREDITO("Crédito total"),
    MIXTO_PARCIAL("Pago parcial + fiado");

    private final String descripcion;

    TipoVentaReporte(String descripcion) {
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
