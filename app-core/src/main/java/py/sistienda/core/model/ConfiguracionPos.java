package py.sistienda.core.model;

public record ConfiguracionPos(
        String prefijoPeso,
        int anchoTicketMm,
        int anchoEtiquetaMm,
        boolean imprimirTicketAutomatico
) {
    public static ConfiguracionPos porDefecto() {
        return new ConfiguracionPos("20", 80, 58, false);
    }
}
