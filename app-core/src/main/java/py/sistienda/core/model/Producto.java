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
        double stockMinimo,
        double stockIdeal,
        boolean activo,
        String codigoBarras,
        Integer pluBalanza
) {
    private static final double EPSILON = 0.000001d;

    public Producto(
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
        this(id, nombre, categoriaId, categoriaNombre, unidadMedida, precioVenta, costo,
                stockActual, 0d, 0d, activo, codigoBarras, pluBalanza);
    }

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
        this(id, nombre, categoriaId, categoriaNombre, unidadMedida, precioVenta, costo,
                stockActual, 0d, 0d, activo, null, null);
    }

    public String identificacionComercial() {
        if (unidadMedida == UnidadMedida.KG && pluBalanza != null) {
            return "PLU " + String.format("%05d", pluBalanza);
        }
        return codigoBarras == null || codigoBarras.isBlank() ? "Sin código" : codigoBarras;
    }

    public boolean reposicionConfigurada() {
        return stockIdeal > EPSILON;
    }

    public boolean necesitaReposicion() {
        return activo && reposicionConfigurada() && stockActual - stockMinimo <= EPSILON;
    }

    public double cantidadSugeridaReposicion() {
        return necesitaReposicion() ? Math.max(0d, stockIdeal - stockActual) : 0d;
    }
}
