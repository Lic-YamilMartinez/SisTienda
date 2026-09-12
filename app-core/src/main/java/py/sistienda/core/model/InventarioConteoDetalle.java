package py.sistienda.core.model;

public record InventarioConteoDetalle(
        String producto,
        UnidadMedida unidadMedida,
        double stockSistema,
        double stockFisico,
        double diferencia
) {
}
