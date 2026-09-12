package py.sistienda.core.model;

public record InventarioConteoItem(
        long productoId,
        String productoNombre,
        UnidadMedida unidadMedida,
        double stockSistema,
        double stockFisico
) {
    public double diferencia() {
        return stockFisico - stockSistema;
    }
}
