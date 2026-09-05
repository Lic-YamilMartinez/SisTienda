package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.BackupInfo;
import py.sistienda.core.repository.BackupRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupServiceTest {

    @Test
    void automatico_reutilizaBackupDelMismoDia() {
        FakeRepository repository = new FakeRepository();
        BackupInfo existing = new BackupInfo(
                Path.of("auto.db"),
                100,
                LocalDateTime.now(),
                true
        );
        repository.backups.add(existing);

        BackupService service = new BackupService(repository);

        assertSame(existing, service.crearAutomaticoSiHaceFalta());
        assertEquals(0, repository.automaticCreated);
    }

    @Test
    void restaurar_rechazaArchivoFueraDeLaLista() {
        BackupService service = new BackupService(new FakeRepository());
        assertThrows(ValidationException.class,
                () -> service.restaurar(Path.of("externo.db")));
    }

    private static final class FakeRepository implements BackupRepository {
        private final List<BackupInfo> backups = new ArrayList<>();
        private int automaticCreated;

        @Override
        public BackupInfo crearManual() {
            BackupInfo info = new BackupInfo(Path.of("manual.db"), 10, LocalDateTime.now(), false);
            backups.add(info);
            return info;
        }

        @Override
        public BackupInfo crearAutomatico() {
            automaticCreated++;
            BackupInfo info = new BackupInfo(Path.of("auto-new.db"), 10, LocalDateTime.now(), true);
            backups.add(info);
            return info;
        }

        @Override
        public List<BackupInfo> listar() {
            return List.copyOf(backups);
        }

        @Override
        public void restaurar(Path backupFile) {
        }

        @Override
        public Path databaseFile() {
            return Path.of("sistienda.db");
        }

        @Override
        public Path backupDirectory() {
            return Path.of("backups");
        }
    }
}
