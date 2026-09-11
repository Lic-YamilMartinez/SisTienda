package py.sistienda.data.repository;

import py.sistienda.core.model.LogoNegocio;
import py.sistienda.core.repository.LogoNegocioRepository;
import py.sistienda.data.database.SqliteConnectionFactory;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class SqliteLogoNegocioRepository implements LogoNegocioRepository {
    private final SqliteConnectionFactory connectionFactory;

    public SqliteLogoNegocioRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public Optional<LogoNegocio> get() {
        String sql = "SELECT logo, mime_type FROM empresa_branding WHERE id = 1";
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql);
             var result = statement.executeQuery()) {
            if (!result.next()) return Optional.empty();
            byte[] content = result.getBytes("logo");
            String mimeType = result.getString("mime_type");
            if (content == null || content.length == 0 || mimeType == null || mimeType.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new LogoNegocio(content, mimeType));
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el logo del negocio.", e);
        }
    }

    @Override
    public LogoNegocio save(LogoNegocio logo) {
        String sql = """
                INSERT INTO empresa_branding (id, logo, mime_type, actualizado_en)
                VALUES (1, ?, ?, datetime('now'))
                ON CONFLICT(id) DO UPDATE SET
                    logo = excluded.logo,
                    mime_type = excluded.mime_type,
                    actualizado_en = excluded.actualizado_en
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, logo.contenido());
            statement.setString(2, logo.mimeType());
            statement.executeUpdate();
            return logo;
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo guardar el logo del negocio.", e);
        }
    }

    @Override
    public void delete() {
        String sql = """
                UPDATE empresa_branding
                SET logo = NULL, mime_type = NULL, actualizado_en = datetime('now')
                WHERE id = 1
                """;
        try (var connection = connectionFactory.open();
             var statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo quitar el logo del negocio.", e);
        }
    }
}
