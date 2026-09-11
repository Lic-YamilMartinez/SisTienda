package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.LogoNegocio;
import py.sistienda.core.repository.LogoNegocioRepository;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class LogoNegocioService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final LogoNegocioRepository repository;

    public LogoNegocioService(LogoNegocioRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Optional<LogoNegocio> obtener() {
        return repository.get();
    }

    public LogoNegocio guardar(byte[] contenido, String nombreArchivo) {
        if (contenido == null || contenido.length == 0) {
            throw new ValidationException("Seleccioná una imagen válida.");
        }
        if (contenido.length > MAX_BYTES) {
            throw new ValidationException("La imagen no puede superar 5 MB.");
        }
        String mimeType = detectarMimeType(nombreArchivo);
        validarFirma(contenido, mimeType);
        return repository.save(new LogoNegocio(contenido, mimeType));
    }

    public void eliminar() {
        repository.delete();
    }

    private String detectarMimeType(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            throw new ValidationException("No pudimos identificar el formato de la imagen.");
        }
        String normalized = nombreArchivo.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        throw new ValidationException("Usá una imagen PNG, JPG o JPEG.");
    }

    private void validarFirma(byte[] contenido, String mimeType) {
        boolean png = contenido.length >= 8
                && (contenido[0] & 0xff) == 0x89
                && contenido[1] == 0x50
                && contenido[2] == 0x4e
                && contenido[3] == 0x47;
        boolean jpeg = contenido.length >= 3
                && (contenido[0] & 0xff) == 0xff
                && (contenido[1] & 0xff) == 0xd8
                && (contenido[2] & 0xff) == 0xff;
        if (("image/png".equals(mimeType) && !png)
                || ("image/jpeg".equals(mimeType) && !jpeg)) {
            throw new ValidationException("El archivo seleccionado no parece ser una imagen válida.");
        }
    }
}
