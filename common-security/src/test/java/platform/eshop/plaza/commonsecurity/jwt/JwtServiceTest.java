package platform.eshop.plaza.commonsecurity.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-bytes-long";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void generatedTokenCarriesUserIdAndRole() {
        String token = jwtService.generateToken("user-1", "SELLER");

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwtService.extractRole(token)).isEqualTo("SELLER");
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService expiring = new JwtService(SECRET, -1_000);

        String token = expiring.generateToken("user-1", "CLIENT");

        assertThat(expiring.isTokenValid(token)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretIsInvalid() {
        JwtService other = new JwtService("another-secret-that-is-also-32-bytes-long!", 60_000);

        String token = other.generateToken("user-1", "CLIENT");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void garbageIsInvalid() {
        assertThat(jwtService.isTokenValid("not-a-token")).isFalse();
    }
}
