package py.sistienda.core.repository;

import py.sistienda.core.model.ImportacionProductoEntrada;
import py.sistienda.core.model.ImportacionProductoResultado;

import java.util.List;
import java.util.Set;

public interface ImportacionProductoRepository {
    Set<String> codigosExistentes(List<String> codigos);

    Set<Integer> plusExistentes(List<Integer> plus);

    ImportacionProductoResultado importar(List<ImportacionProductoEntrada> productos, long usuarioId);
}
