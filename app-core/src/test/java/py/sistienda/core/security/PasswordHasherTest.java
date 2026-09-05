package py.sistienda.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashYVerify_funcionanConLaMismaContrasena() {
        PasswordHasher hasher = new PasswordHasher();
        char[] password = "segura123".toCharArray();

        String hash = hasher.hash(password);

        assertTrue(hasher.verify("segura123".toCharArray(), hash));
        assertFalse(hasher.verify("incorrecta".toCharArray(), hash));
    }
}
