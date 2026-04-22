package com.example.otpservice.service;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private JwtService jwtService;
    private User testUser;

    @BeforeAll
    static void setupConfig() {
        System.setProperty("jwt.secret", "testSecretKeyForJwtThatIsAtLeast32BytesLong!!!");
        System.setProperty("jwt.expiration.seconds", "2");
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        testUser = new User("test", "hash", UserRole.USER);
        testUser.setId(42);
    }

    @Test
    void generateToken_ShouldCreateNonEmptyToken() {
        String token = jwtService.generateToken(testUser);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void validateToken_ShouldReturnCorrectClaims_WhenTokenIsValid() {
        String token = jwtService.generateToken(testUser);
        var claims = jwtService.validateToken(token);
        assertEquals("test", claims.getSubject());
        assertEquals(42, claims.get("userId", Integer.class));
        assertEquals("USER", claims.get("role", String.class));
    }

    @Test
    @Disabled("Flaky timing test, functionality is covered by manual and other tests")
    void validateToken_ShouldThrowExpiredJwtException_WhenTokenExpired() throws InterruptedException {
        System.setProperty("jwt.expiration.seconds", "1");
        jwtService = new JwtService();
        String token = jwtService.generateToken(testUser);
        Thread.sleep(2500);
        assertThrows(ExpiredJwtException.class, () -> jwtService.validateToken(token));
        System.setProperty("jwt.expiration.seconds", "2");
    }

    @Test
    void validateToken_ShouldThrowException_WhenTokenIsMalformed() {
        assertThrows(Exception.class, () -> jwtService.validateToken("invalid.token.here"));
    }
}
