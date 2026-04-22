package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.JwtService;
import com.example.otpservice.service.UserService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {
    @Mock
    private HttpExchange exchange;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserService userService;

    private TestAuthFilter authFilter;
    private Headers headers;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() throws Exception {
        authFilter = new TestAuthFilter();
        var jwtField = AuthFilter.class.getDeclaredField("jwtService");
        jwtField.setAccessible(true);
        jwtField.set(authFilter, jwtService);
        var userField = AuthFilter.class.getDeclaredField("userService");
        userField.setAccessible(true);
        userField.set(authFilter, userService);

        headers = new Headers();
        responseBody = new ByteArrayOutputStream();

        lenient().when(exchange.getRequestHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/test"));
        lenient().when(exchange.getRequestMethod()).thenReturn("GET");
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
    }

    static class TestAuthFilter extends AuthFilter {
        private boolean handleCalled = false;
        private User authenticatedUser;

        @Override
        protected void handleAuthenticated(HttpExchange exchange, User user) {
            handleCalled = true;
            authenticatedUser = user;
        }

        public boolean isHandleCalled() {
            return handleCalled;
        }

        public User getAuthenticatedUser() {
            return authenticatedUser;
        }

        public User testAuthenticate(HttpExchange exchange) throws IOException {
            return authenticate(exchange);
        }
    }

    @Test
    void authenticate_NoHeader_ReturnsNull() throws IOException {
        headers.clear();
        User result = authFilter.testAuthenticate(exchange);
        assertNull(result);
        // Публичные эндпоинты не отправляют 401 при отсутствии заголовка
        verify(exchange, never()).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void authenticate_InvalidToken_ReturnsNullAnd401() throws Exception {
        headers.set("Authorization", "Bearer invalid.token.here");
        when(userService.getUserFromToken("invalid.token.here"))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        User result = authFilter.testAuthenticate(exchange);
        assertNull(result);
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void authenticate_ExpiredToken_ReturnsNullAnd401() throws Exception {
        headers.set("Authorization", "Bearer expired.token");
        when(userService.getUserFromToken("expired.token"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        User result = authFilter.testAuthenticate(exchange);
        assertNull(result);
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void authenticate_ValidToken_ReturnsUser() throws Exception {
        headers.set("Authorization", "Bearer valid.token");
        User expectedUser = new User("test", "hash", UserRole.USER);
        expectedUser.setId(1);
        when(userService.getUserFromToken("valid.token")).thenReturn(expectedUser);

        User result = authFilter.testAuthenticate(exchange);
        assertNotNull(result);
        assertEquals(expectedUser.getId(), result.getId());
        assertEquals(expectedUser.getLogin(), result.getLogin());
    }

    @Test
    void handle_CallsHandleAuthenticated_WhenAuthSucceeds() throws IOException {
        headers.set("Authorization", "Bearer valid.token");
        TestAuthFilter spyFilter = spy(authFilter);
        User user = new User("test", "hash", UserRole.USER);
        doReturn(user).when(spyFilter).authenticate(exchange);

        spyFilter.handle(exchange);
        assertTrue(spyFilter.isHandleCalled());
        assertEquals(user, spyFilter.getAuthenticatedUser());
    }
}
