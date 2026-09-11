package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.LogoNegocio;
import py.sistienda.core.repository.LogoNegocioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoNegocioServiceTest {

    @Test
    void guardaPngValidoYPermiteEliminarlo() {
        FakeRepository repository = new FakeRepository();
        LogoNegocioService service = new LogoNegocioService(repository);
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};

        LogoNegocio saved = service.guardar(png, "mi-logo.PNG");

        assertEquals("image/png", saved.mimeType());
        assertArrayEquals(png, service.obtener().orElseThrow().contenido());
        service.eliminar();
        assertTrue(service.obtener().isEmpty());
    }

    @Test
    void rechazaExtensionNoPermitidaOFirmaFalsa() {
        LogoNegocioService service = new LogoNegocioService(new FakeRepository());
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4};

        assertThrows(ValidationException.class, () -> service.guardar(png, "logo.gif"));
        assertThrows(ValidationException.class, () -> service.guardar(new byte[]{1, 2, 3, 4}, "logo.png"));
    }

    @Test
    void rechazaArchivosMayoresACincoMegabytes() {
        LogoNegocioService service = new LogoNegocioService(new FakeRepository());
        byte[] huge = new byte[LogoNegocioService.MAX_BYTES + 1];
        huge[0] = (byte) 0x89;
        huge[1] = 0x50;
        huge[2] = 0x4e;
        huge[3] = 0x47;

        assertThrows(ValidationException.class, () -> service.guardar(huge, "logo.png"));
    }

    private static final class FakeRepository implements LogoNegocioRepository {
        private LogoNegocio logo;

        @Override
        public Optional<LogoNegocio> get() {
            return Optional.ofNullable(logo);
        }

        @Override
        public LogoNegocio save(LogoNegocio logo) {
            this.logo = logo;
            return logo;
        }

        @Override
        public void delete() {
            logo = null;
        }
    }
}
