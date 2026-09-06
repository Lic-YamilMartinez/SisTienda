package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.repository.ConfiguracionPosRepository;

import java.util.Objects;

public final class ConfiguracionPosService {
    private final ConfiguracionPosRepository repository;

    public ConfiguracionPosService(ConfiguracionPosRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public ConfiguracionPos obtener() {
        return repository.get();
    }

    public ConfiguracionPos guardar(String prefijoPeso, int anchoTicketMm, int anchoEtiquetaMm,
                                    boolean imprimirTicketAutomatico) {
        String prefijo = prefijoPeso == null ? "" : prefijoPeso.trim();
        if (!prefijo.matches("2\\d")) {
            throw new ValidationException("El prefijo de balanza debe tener 2 dígitos y comenzar con 2 (20 a 29).");
        }
        if (anchoTicketMm != 58 && anchoTicketMm != 80) {
            throw new ValidationException("El ancho de ticket debe ser 58 mm u 80 mm.");
        }
        if (anchoEtiquetaMm < 40 || anchoEtiquetaMm > 100) {
            throw new ValidationException("El ancho de etiqueta debe estar entre 40 y 100 mm.");
        }
        return repository.save(new ConfiguracionPos(prefijo, anchoTicketMm, anchoEtiquetaMm, imprimirTicketAutomatico));
    }
}
