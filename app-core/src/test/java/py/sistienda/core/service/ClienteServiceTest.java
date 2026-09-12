package py.sistienda.core.service;

import org.junit.jupiter.api.Test;
import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.AbonoClienteResultado;
import py.sistienda.core.model.CajaSesion;
import py.sistienda.core.model.Cliente;
import py.sistienda.core.model.ClienteCuentaMovimiento;
import py.sistienda.core.model.ClienteCuentaResumen;
import py.sistienda.core.model.EstadoCaja;
import py.sistienda.core.model.MetodoPago;
import py.sistienda.core.model.Usuario;
import py.sistienda.core.repository.ClienteRepository;
import py.sistienda.core.security.AutorizacionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClienteServiceTest {

    @Test
    void soloNombreEsObligatorioAlCrearCliente() {
        FakeRepository repository = new FakeRepository();
        ClienteService service = new ClienteService(repository, new AutorizacionService());
        Usuario owner = user("DUENIO");

        Cliente cliente = service.crear(owner, "  María   González  ", "", null, "", null);

        assertEquals("María González", cliente.nombre());
        assertEquals(null, cliente.documento());
        assertEquals(null, cliente.telefono());
        assertThrows(ValidationException.class,
                () -> service.crear(owner, "   ", null, null, null, null));
    }

    @Test
    void vendedorNoPuedeGestionarFiadoPeroCajeroSi() {
        ClienteService service = new ClienteService(new FakeRepository(), new AutorizacionService());

        assertThrows(ValidationException.class,
                () -> service.buscar(user("VENDEDOR"), ""));
        service.buscar(user("CAJERO"), "");
    }

    @Test
    void abonoNoPuedeSuperarLaDeudaNiUsarFiadoComoFormaDeCobro() {
        FakeRepository repository = new FakeRepository();
        repository.balance = 100_000d;
        ClienteService service = new ClienteService(repository, new AutorizacionService());
        Usuario owner = user("DUENIO");
        CajaSesion caja = new CajaSesion(
                10, owner.id(), LocalDateTime.now(), null, 0, null, EstadoCaja.ABIERTA, null
        );

        assertThrows(ValidationException.class,
                () -> service.registrarAbono(owner, caja, repository.cliente, MetodoPago.EFECTIVO, 100_001d, null));
        assertThrows(ValidationException.class,
                () -> service.registrarAbono(owner, caja, repository.cliente, MetodoPago.FIADO, 10_000d, null));

        var result = service.registrarAbono(
                owner, caja, repository.cliente, MetodoPago.TRANSFERENCIA, 40_000d, "parcial"
        );
        assertEquals(60_000d, result.saldoNuevo(), 0.001d);
    }

    private Usuario user(String role) {
        return new Usuario(1, "test", role, true);
    }

    private static final class FakeRepository implements ClienteRepository {
        private Cliente cliente = new Cliente(1, "Cliente", null, null, null, null, true);
        private double balance;

        @Override
        public List<ClienteCuentaResumen> buscar(String query) {
            return List.of(new ClienteCuentaResumen(cliente, balance, null));
        }

        @Override
        public Optional<Cliente> findById(long clienteId) {
            return clienteId == cliente.id() ? Optional.of(cliente) : Optional.empty();
        }

        @Override
        public Cliente create(String nombre, String documento, String telefono, String direccion, String nota) {
            cliente = new Cliente(1, nombre, documento, telefono, direccion, nota, true);
            return cliente;
        }

        @Override
        public Cliente update(long clienteId, String nombre, String documento, String telefono, String direccion, String nota) {
            cliente = new Cliente(clienteId, nombre, documento, telefono, direccion, nota, true);
            return cliente;
        }

        @Override
        public double saldo(long clienteId) {
            return balance;
        }

        @Override
        public List<ClienteCuentaMovimiento> movimientos(long clienteId) {
            return List.of();
        }

        @Override
        public AbonoClienteResultado registrarAbono(long clienteId, long cajaSesionId, long usuarioId,
                                                     MetodoPago metodoPago, double monto, String observacion) {
            double previous = balance;
            balance -= monto;
            return new AbonoClienteResultado(1, monto, previous, balance, metodoPago);
        }
    }
}
