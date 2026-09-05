package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.BackupInfo;
import py.sistienda.core.repository.BackupRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class BackupService {

    private final BackupRepository repository;

    public BackupService(BackupRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public BackupInfo crearManual() {
        return repository.crearManual();
    }

    public BackupInfo crearAutomaticoSiHaceFalta() {
        LocalDate hoy = LocalDate.now();
        return repository.listar().stream()
                .filter(BackupInfo::automatico)
                .filter(info -> info.creadoEn().toLocalDate().equals(hoy))
                .findFirst()
                .orElseGet(repository::crearAutomatico);
    }

    public List<BackupInfo> listar() {
        return repository.listar();
    }

    public void restaurar(Path archivo) {
        if (archivo == null) {
            throw new ValidationException("Seleccioná un backup para restaurar.");
        }
        boolean perteneceALaLista = repository.listar().stream()
                .map(BackupInfo::archivo)
                .anyMatch(path -> path.toAbsolutePath().normalize().equals(archivo.toAbsolutePath().normalize()));
        if (!perteneceALaLista) {
            throw new ValidationException("El archivo seleccionado no pertenece a los backups de SisTienda.");
        }
        repository.restaurar(archivo);
    }

    public Path databaseFile() {
        return repository.databaseFile();
    }

    public Path backupDirectory() {
        return repository.backupDirectory();
    }
}
