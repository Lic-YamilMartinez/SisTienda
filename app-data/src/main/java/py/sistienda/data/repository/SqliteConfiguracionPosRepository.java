package py.sistienda.data.repository;

import py.sistienda.core.model.ConfiguracionPos;
import py.sistienda.core.repository.ConfiguracionPosRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.util.Objects;

public final class SqliteConfiguracionPosRepository implements ConfiguracionPosRepository {
    private final SqliteConnectionFactory connectionFactory;

    public SqliteConfiguracionPosRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public ConfiguracionPos get() {
        String sql = "SELECT prefijo_peso, ancho_ticket_mm, ancho_etiqueta_mm, imprimir_ticket_automatico FROM configuracion_pos WHERE id = 1";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            if (!result.next()) return ConfiguracionPos.porDefecto();
            return new ConfiguracionPos(
                    result.getString("prefijo_peso"),
                    result.getInt("ancho_ticket_mm"),
                    result.getInt("ancho_etiqueta_mm"),
                    result.getInt("imprimir_ticket_automatico") == 1
            );
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar la configuración POS.", e);
        }
    }

    @Override
    public ConfiguracionPos save(ConfiguracionPos configuracion) {
        String sql = """
                INSERT INTO configuracion_pos
                    (id, prefijo_peso, ancho_ticket_mm, ancho_etiqueta_mm, imprimir_ticket_automatico)
                VALUES (1, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    prefijo_peso = excluded.prefijo_peso,
                    ancho_ticket_mm = excluded.ancho_ticket_mm,
                    ancho_etiqueta_mm = excluded.ancho_etiqueta_mm,
                    imprimir_ticket_automatico = excluded.imprimir_ticket_automatico
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, configuracion.prefijoPeso());
            statement.setInt(2, configuracion.anchoTicketMm());
            statement.setInt(3, configuracion.anchoEtiquetaMm());
            statement.setInt(4, configuracion.imprimirTicketAutomatico() ? 1 : 0);
            statement.executeUpdate();
            return configuracion;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo guardar la configuración POS.", e);
        }
    }
}
