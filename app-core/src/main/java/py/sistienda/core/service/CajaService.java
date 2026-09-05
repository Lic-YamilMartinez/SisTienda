package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.ResumenVentasCaja;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.CajaRepository;

import java.util.Objects;
import java.util.Optional;

public final class CajaService {

    private final CajaRepository cajaRepository;

    public CajaService(CajaRepository cajaRepository) {
        this.cajaRepository = Objects.requireNonNull(cajaRepository);
    }

    public Optional<CajaSesion> obtenerAbierta(Usuario usuario) {
        Objects.requireNonNull(usuario);
        return cajaRepository.findOpenByUser(usuario.id());
    }

    public ResumenVentasCaja resumenVentas(CajaSesion sesion) {
        Objects.requireNonNull(sesion);
        return cajaRepository.salesSummary(sesion.id());
    }

    public CajaSesion abrir(Usuario usuario, double montoApertura, String notas) {
        Objects.requireNonNull(usuario);
        validarMonto(montoApertura, "El monto de apertura");

        if (cajaRepository.findOpenByUser(usuario.id()).isPresent()) {
            throw new ValidationException("Ya tenés una caja abierta.");
        }

        return cajaRepository.open(usuario.id(), montoApertura, normalizarNotas(notas));
    }

    public CajaSesion cerrar(CajaSesion sesion, double montoCierre, String notas) {
        Objects.requireNonNull(sesion);
        if (!sesion.abierta()) {
            throw new ValidationException("La caja ya está cerrada.");
        }
        validarMonto(montoCierre, "El monto de cierre");
        return cajaRepository.close(sesion.id(), montoCierre, normalizarNotas(notas));
    }

    private void validarMonto(double monto, String campo) {
        if (!Double.isFinite(monto) || monto < 0) {
            throw new ValidationException(campo + " debe ser igual o mayor a cero.");
        }
    }

    private String normalizarNotas(String notas) {
        if (notas == null || notas.isBlank()) {
            return null;
        }
        String normalized = notas.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 300) {
            throw new ValidationException("Las notas son demasiado largas.");
        }
        return normalized;
    }
}
