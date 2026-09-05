package py.sistienda.core.repository;

import py.sistienda.core.model.BackupInfo;

import java.nio.file.Path;
import java.util.List;

public interface BackupRepository {

    BackupInfo crearManual();

    BackupInfo crearAutomatico();

    List<BackupInfo> listar();

    void restaurar(Path backupFile);

    Path databaseFile();

    Path backupDirectory();
}
