package py.sistienda.core.repository;

import py.sistienda.core.model.ImportacionProductoEntrada;
import py.sistienda.core.model.ImportacionProductoResultado;

import java.util.List;
import java.util.Set;

public interface ImportacionProductoRepository {
    Set<String> codigosExistentes(List<String> codigos);

    Set<Integer> plusExistentes(List<Integer> plus);

    /**
     * Claves normalizadas nombre|unidad|categoria de productos existentes.
     * Se usa como red de seguridad cuando el archivo no trae código/PLU.
     */
    Set<String> clavesProductoExistentes();

    ImportacionProductoResultado importar(List<ImportacionProductoEntrada> productos, long usuarioId);
}
