package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.AbonoClienteResultado;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.ClienteRepository;
import py.sistienda.core.security.AutorizacionService;
import py.sistienda.core.security.Permiso;

import java.util.List;
import java.util.Objects;

public final class ClienteService {
    private static final double EPSILON = 0.000001d;

    private final ClienteRepository repository;
    private final AutorizacionService autorizacionService;

    public ClienteService(ClienteRepository repository, AutorizacionService autorizacionService) {
        this.repository = Objects.requireNonNull(repository);
        this.autorizacionService = Objects.requireNonNull(autorizacionService);
    }

    public List<ClienteCuentaResumen> buscar(Usuario usuario, String query) {
        exigirPermiso(usuario);
        String normalized = query == null ? "" : query.trim();
        return repository.buscar(normalized);
    }

    public Cliente obtener(Usuario usuario, long clienteId) {
        exigirPermiso(usuario);
        validarId(clienteId);
        return repository.findById(clienteId)
                .orElseThrow(() -> new ValidationException("No se encontró el cliente seleccionado."));
    }

    public Cliente crear(Usuario usuario, String nombre, String documento, String telefono,
                         String direccion, String nota) {
        exigirPermiso(usuario);
        return repository.create(
                normalizarNombre(nombre),
                normalizarOpcional(documento, 40),
                normalizarOpcional(telefono, 40),
                normalizarOpcional(direccion, 180),
                normalizarOpcional(nota, 300)
        );
    }

    public Cliente actualizar(Usuario usuario, long clienteId, String nombre, String documento,
                              String telefono, String direccion, String nota) {
        exigirPermiso(usuario);
        validarId(clienteId);
        return repository.update(
                clienteId,
                normalizarNombre(nombre),
                normalizarOpcional(documento, 40),
                normalizarOpcional(telefono, 40),
                normalizarOpcional(direccion, 180),
                normalizarOpcional(nota, 300)
        );
    }

    public double saldo(Usuario usuario, long clienteId) {
        exigirPermiso(usuario);
        validarId(clienteId);
        return repository.saldo(clienteId);
    }

    public List<ClienteCuentaMovimiento> movimientos(Usuario usuario, long clienteId) {
        exigirPermiso(usuario);
        validarId(clienteId);
        repository.findById(clienteId)
                .orElseThrow(() -> new ValidationException("No se encontró el cliente seleccionado."));
        return repository.movimientos(clienteId);
    }

    public AbonoClienteResultado registrarAbono(
            Usuario usuario,
            CajaSesion caja,
            Cliente cliente,
            MetodoPago metodoPago,
            double monto,
            String observacion
    ) {
        exigirPermiso(usuario);
        Objects.requireNonNull(caja);
        Objects.requireNonNull(cliente);
        if (!caja.abierta()) {
            throw new ValidationException("Abrí una caja para registrar el cobro del fiado.");
        }
        if (caja.usuarioId() != usuario.id()) {
            throw new ValidationException("La caja abierta pertenece a otro usuario.");
        }
        if (!cliente.activo()) {
            throw new ValidationException("El cliente está inactivo.");
        }
        if (metodoPago == null || metodoPago == MetodoPago.FIADO) {
            throw new ValidationException("Elegí cómo pagó el cliente: efectivo, transferencia o tarjeta.");
        }
        if (!Double.isFinite(monto) || monto <= 0) {
            throw new ValidationException("El monto del abono debe ser mayor a cero.");
        }
        double saldoActual = repository.saldo(cliente.id());
        if (saldoActual <= EPSILON) {
            throw new ValidationException("Este cliente no tiene deuda pendiente para cobrar.");
        }
        if (monto - saldoActual > EPSILON) {
            throw new ValidationException("El abono no puede superar el saldo pendiente de la cuenta.");
        }
        return repository.registrarAbono(
                cliente.id(), caja.id(), usuario.id(), metodoPago, monto,
                normalizarOpcional(observacion, 200)
        );
    }

    public boolean puedeGestionar(Usuario usuario) {
        return usuario != null && autorizacionService.puede(usuario, Permiso.FIADO_GESTIONAR);
    }

    private void exigirPermiso(Usuario usuario) {
        Objects.requireNonNull(usuario);
        autorizacionService.exigir(usuario, Permiso.FIADO_GESTIONAR);
    }

    private void validarId(long id) {
        if (id <= 0) throw new ValidationException("El cliente seleccionado no es válido.");
    }

    private String normalizarNombre(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Ingresá el nombre del cliente.");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2) {
            throw new ValidationException("El nombre del cliente es demasiado corto.");
        }
        if (normalized.length() > 120) {
            throw new ValidationException("El nombre del cliente no puede superar 120 caracteres.");
        }
        return normalized;
    }

    private String normalizarOpcional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > max) {
            throw new ValidationException("Uno de los datos del cliente es demasiado largo.");
        }
        return normalized;
    }
}
