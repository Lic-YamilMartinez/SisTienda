package py.sistienda.core.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record BackupInfo(
        Path archivo,
        long bytes,
        LocalDateTime creadoEn,
        boolean automatico
) {
    public String nombreArchivo() {
        return archivo.getFileName().toString();
    }
}
