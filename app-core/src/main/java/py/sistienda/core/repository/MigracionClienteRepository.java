package py.sistienda.core.repository;

import py.sistienda.core.model.ImportacionClienteSaldoEntrada;
import py.sistienda.core.model.ImportacionClienteSaldoResultado;
import py.sistienda.core.model.MigracionClienteCoincidencia;
import py.sistienda.core.model.SaldoInicialClienteResultado;

import java.time.LocalDate;
import java.util.List;

public interface MigracionClienteRepository {
    MigracionClienteCoincidencia buscarCoincidencia(String nombre, String documento, String telefono);

    SaldoInicialClienteResultado registrarSaldoInicial(
            long clienteId,
            long usuarioId,
            double monto,
            LocalDate fechaReferencia,
            String referencia,
            String observacion
    );

    ImportacionClienteSaldoResultado importarSaldosIniciales(
            List<ImportacionClienteSaldoEntrada> entradas,
            long usuarioId,
            String referenciaLote
    );
}
