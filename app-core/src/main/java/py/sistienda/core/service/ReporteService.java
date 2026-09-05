package py.sistienda.core.service;

import py.sistienda.core.model.ReporteDiario;
import py.sistienda.core.model.VentaDetalle;
import py.sistienda.core.model.VentaResumen;
import py.sistienda.core.repository.ReporteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class ReporteService {

    private final ReporteRepository reporteRepository;

    public ReporteService(ReporteRepository reporteRepository) {
        this.reporteRepository = Objects.requireNonNull(reporteRepository);
    }

    public ReporteDiario resumenDiario(LocalDate fecha) {
        return reporteRepository.resumenDiario(Objects.requireNonNull(fecha));
    }

    public List<VentaResumen> listarVentas(LocalDate fecha) {
        return reporteRepository.listarVentas(Objects.requireNonNull(fecha));
    }

    public VentaDetalle detalleVenta(long ventaId) {
        if (ventaId <= 0) {
            throw new IllegalArgumentException("La venta debe ser válida.");
        }
        return reporteRepository.detalleVenta(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la venta."));
    }
}
