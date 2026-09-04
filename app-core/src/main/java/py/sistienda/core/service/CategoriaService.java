package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CategoriaProducto;
import py.sistienda.core.repository.CategoriaRepository;

import java.util.List;
import java.util.Objects;

public final class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = Objects.requireNonNull(categoriaRepository);
    }

    public List<CategoriaProducto> listarActivas() {
        return categoriaRepository.findAllActive();
    }

    public CategoriaProducto crear(String nombre) {
        String nombreNormalizado = normalizarNombre(nombre);
        return categoriaRepository.create(nombreNormalizado);
    }

    private String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ValidationException("Ingresá un nombre para la categoría.");
        }

        String normalizado = nombre.trim().replaceAll("\\s+", " ");
        if (normalizado.length() > 60) {
            throw new ValidationException("El nombre de la categoría es demasiado largo.");
        }
        return normalizado;
    }
}
