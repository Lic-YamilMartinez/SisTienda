package py.sistienda.core.repository;

import py.sistienda.core.model.CompraDetalle;
import py.sistienda.core.model.CompraResumen;
import py.sistienda.core.model.LineaCompra;
import py.sistienda.core.model.Proveedor;
import py.sistienda.core.model.Usuario;

import java.util.List;

public interface CompraRepository {
    long registrar(Usuario usuario, Proveedor proveedor, String nroDocumento, List<LineaCompra> lineas, String observacion);
    List<CompraResumen> listarRecientes(int limite);
    CompraDetalle detalle(long compraId);
}
