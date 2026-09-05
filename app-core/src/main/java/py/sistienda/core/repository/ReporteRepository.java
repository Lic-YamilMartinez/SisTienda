package py.sistienda.core.repository;

import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaResumen;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReporteRepository {

    ReporteDiario resumenDiario(LocalDate fecha);

    List<VentaResumen> listarVentas(LocalDate fecha);

    Optional<VentaDetalle> detalleVenta(long ventaId);
}
