package py.sistienda.core.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodigoBarrasServiceTest {

    private final CodigoBarrasService service = new CodigoBarrasService();

    @Test
    void generaCodigoInternoEan13Valido() {
        String codigo = service.generarCodigoInterno(42);

        assertEquals(13, codigo.length());
        assertTrue(codigo.startsWith("290"));
        assertTrue(service.esEan13Valido(codigo));
    }

    @Test
    void generaYDecodificaEtiquetaDePesoConPluYGramos() {
        String codigo = service.generarCodigoPeso("20", 15, 0.735);

        assertEquals(13, codigo.length());
        assertTrue(service.esEan13Valido(codigo));
        var lectura = service.decodificarCodigoPeso(codigo, "20").orElseThrow();
        assertEquals(15, lectura.plu());
        assertEquals(0.735, lectura.pesoKg(), 0.000001);
    }

    @Test
    void rechazaEtiquetaConPrefijoOChecksumIncorrecto() {
        String codigo = service.generarCodigoPeso("20", 321, 1.250);

        assertTrue(service.decodificarCodigoPeso(codigo, "21").isEmpty());
        char ultimo = codigo.charAt(12);
        char alterado = ultimo == '9' ? '0' : (char) (ultimo + 1);
        String invalido = codigo.substring(0, 12) + alterado;
        assertFalse(service.esEan13Valido(invalido));
        assertTrue(service.decodificarCodigoPeso(invalido, "20").isEmpty());
    }
}
