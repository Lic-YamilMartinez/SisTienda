package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.model.CategoriaProducto;
import py.sistienda.core.repository.CategoriaRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoriaServiceTest {

    @Test
    void listarActivas_delegaEnRepositorioYDevuelveResultado() {
        List<CategoriaProducto> esperadas = List.of(
                new CategoriaProducto(1L, "Alimentos", true),
                new CategoriaProducto(2L, "Bebidas", true)
        );
        CategoriaRepository repository = () -> esperadas;
        CategoriaService service = new CategoriaService(repository);

        List<CategoriaProducto> resultado = service.listarActivas();

        assertEquals(esperadas, resultado);
    }

    @Test
    void constructor_rechazaRepositorioNulo() {
        assertThrows(NullPointerException.class, () -> new CategoriaService(null));
    }
}
