package py.sistienda.core.service;

import py.sistienda.core.exception.ValidationException;
import py.sistienda.core.model.LecturaCodigoPeso;

import java.util.Optional;

public final class CodigoBarrasService {

    public String generarCodigoInterno(long productoId) {
        if (productoId <= 0 || productoId > 999_999_999L) {
            throw new ValidationException("No se puede generar un código interno para ese producto.");
        }
        String base = "290" + String.format("%09d", productoId);
        return base + calcularDigitoEan13(base);
    }

    public String generarCodigoPeso(String prefijo, int plu, double pesoKg) {
        validarPrefijo(prefijo);
        if (plu < 0 || plu > 99_999) {
            throw new ValidationException("El PLU de balanza debe estar entre 0 y 99999.");
        }
        if (!Double.isFinite(pesoKg) || pesoKg <= 0) {
            throw new ValidationException("El peso debe ser mayor a cero.");
        }
        long gramos = Math.round(pesoKg * 1000d);
        if (gramos <= 0 || gramos > 99_999) {
            throw new ValidationException("La etiqueta admite pesos de hasta 99,999 kg.");
        }
        String base = prefijo + String.format("%05d", plu) + String.format("%05d", gramos);
        return base + calcularDigitoEan13(base);
    }

    public Optional<LecturaCodigoPeso> decodificarCodigoPeso(String codigo, String prefijo) {
        validarPrefijo(prefijo);
        if (codigo == null) return Optional.empty();
        String value = codigo.trim();
        if (!value.matches("\\d{13}") || !value.startsWith(prefijo) || !esEan13Valido(value)) {
            return Optional.empty();
        }
        int plu = Integer.parseInt(value.substring(2, 7));
        int gramos = Integer.parseInt(value.substring(7, 12));
        if (gramos <= 0) return Optional.empty();
        return Optional.of(new LecturaCodigoPeso(plu, gramos / 1000d, value));
    }

    public boolean esEan13Valido(String codigo) {
        if (codigo == null || !codigo.matches("\\d{13}")) return false;
        return codigo.charAt(12) - '0' == calcularDigitoEan13(codigo.substring(0, 12));
    }

    public int calcularDigitoEan13(String base12) {
        if (base12 == null || !base12.matches("\\d{12}")) {
            throw new ValidationException("La base EAN-13 debe tener 12 dígitos.");
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = base12.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    private void validarPrefijo(String prefijo) {
        if (prefijo == null || !prefijo.matches("2\\d")) {
            throw new ValidationException("El prefijo de balanza debe estar entre 20 y 29.");
        }
    }
}
