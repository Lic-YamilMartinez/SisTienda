package py.sistienda.core.model;

public record Producto(
        long id,
        String nombre,
        Long categoriaId,
        String categoriaNombre,
        UnidadMedida unidadMedida,
        double precioVenta,
        double costo,
        double stockActual,
        boolean activo,
        String codigoBarras,
        Integer pluBalanza
) {
    public Producto(
            long id,
            String nombre,
            Long categoriaId,
            String categoriaNombre,
            UnidadMedida unidadMedida,
            double precioVenta,
            double costo,
            double stockActual,
            boolean activo
    ) {
        this(id, nombre, categoriaId, categoriaNombre, unidadMedida, precioVenta, costo, stockActual, activo, null, null);
    }

    public String identificacionComercial() {
        if (unidadMedida == UnidadMedida.KG && pluBalanza != null) {
            return "PLU " + String.format("%05d", pluBalanza);
        }
        return codigoBarras == null || codigoBarras.isBlank() ? "Sin código" : codigoBarras;
    }
}
