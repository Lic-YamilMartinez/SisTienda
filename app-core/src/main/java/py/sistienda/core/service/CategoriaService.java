package py.sistienda.core.service;

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
}
