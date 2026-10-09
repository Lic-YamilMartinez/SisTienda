package py.sistienda.core.model;

public record ReporteFiltroOpcion(
        long id,
        String etiqueta
) {
    @Override
    public String toString() {
        return etiqueta;
    }
}
