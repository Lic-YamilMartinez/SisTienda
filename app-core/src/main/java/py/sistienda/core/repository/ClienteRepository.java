package py.sistienda.core.repository;

import py.sistienda.core.model.AbonoClienteResultado;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.MetodoPago;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository {
    List<ClienteCuentaResumen> buscar(String query);

    Optional<Cliente> findById(long clienteId);

    Cliente create(String nombre, String documento, String telefono, String direccion, String nota);

    Cliente update(long clienteId, String nombre, String documento, String telefono, String direccion, String nota);

    double saldo(long clienteId);

    List<ClienteCuentaMovimiento> movimientos(long clienteId);

    AbonoClienteResultado registrarAbono(
            long clienteId,
            long cajaSesionId,
            long usuarioId,
            MetodoPago metodoPago,
            double monto,
            String observacion
    );
}
