package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ArqueoCajaDetalle;
import py.sistienda.core.model.ArqueoCajaResumen;
import py.sistienda.core.repository.ArqueoCajaRepository;

import java.util.List;
import java.util.Objects;

public final class ArqueoCajaService {
    private final ArqueoCajaRepository repository;

    public ArqueoCajaService(ArqueoCajaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<ArqueoCajaResumen> listarRecientes() {
        return repository.findRecent(100);
    }

    public ArqueoCajaDetalle detalle(long cajaId) {
        if (cajaId <= 0) {
            throw new ValidationException("La caja seleccionada no es válida.");
        }
        return repository.findDetail(cajaId);
    }
}
