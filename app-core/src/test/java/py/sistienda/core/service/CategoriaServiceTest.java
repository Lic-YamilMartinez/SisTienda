package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
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
        CategoriaRepository repository = repository(esperadas);
        CategoriaService service = new CategoriaService(repository);

        assertEquals(esperadas, service.listarActivas());
    }

    @Test
    void crear_normalizaNombreAntesDePersistir() {
        CategoriaRepository repository = new CategoriaRepository() {
            @Override
            public List<CategoriaProducto> findAllActive() {
                return List.of();
            }

            @Override
            public CategoriaProducto create(String nombre) {
                return new CategoriaProducto(10L, nombre, true);
            }
        };
        CategoriaService service = new CategoriaService(repository);

        CategoriaProducto creada = service.crear("  Bebidas   frías  ");

        assertEquals("Bebidas frías", creada.nombre());
    }

    @Test
    void crear_rechazaNombreVacio() {
        CategoriaService service = new CategoriaService(repository(List.of()));

        assertThrows(ValidationException.class, () -> service.crear("   "));
    }

    @Test
    void constructor_rechazaRepositorioNulo() {
        assertThrows(NullPointerException.class, () -> new CategoriaService(null));
    }

    private CategoriaRepository repository(List<CategoriaProducto> categorias) {
        return new CategoriaRepository() {
            @Override
            public List<CategoriaProducto> findAllActive() {
                return categorias;
            }

            @Override
            public CategoriaProducto create(String nombre) {
                return new CategoriaProducto(1L, nombre, true);
            }
        };
    }
}
