package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.UserService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterHandlerTest {
    @Mock private UserService userService;
    @Mock private HttpExchange exchange;
    private RegisterHandler handler;
    private ByteArrayOutputStream responseBody;
    private Headers headers;

    @BeforeEach
    void setUp() throws Exception {
        handler = new RegisterHandler();
        var field = RegisterHandler.class.getDeclaredField("userService");
        field.setAccessible(true);
        field.set(handler, userService);

        responseBody = new ByteArrayOutputStream();
        headers = new Headers();

        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestMethod()).thenReturn("POST");
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/register"));
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getRequestHeaders()).thenReturn(new Headers());
    }

    @Test
    void register_Success() throws Exception {
        String json = "{\"login\":\"john\",\"password\":\"pass\",\"role\":\"USER\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        User saved = new User("john", "hash", UserRole.USER);
        saved.setId(1);
        when(userService.register("john", "pass", UserRole.USER)).thenReturn(saved);

        handler.handleAuthenticated(exchange, null);

        verify(exchange).sendResponseHeaders(eq(201), anyLong());
        assertTrue(responseBody.toString().contains("john"));
    }

    @Test
    void register_MissingLogin_Returns400() throws Exception {
        String json = "{\"password\":\"pass\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, null);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void register_SecondAdmin_Returns400() throws Exception {
        String json = "{\"login\":\"admin2\",\"password\":\"pass\",\"role\":\"ADMIN\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(userService.register("admin2", "pass", UserRole.ADMIN))
                .thenThrow(new IllegalArgumentException("Administrator already exists"));

        handler.handleAuthenticated(exchange, null);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }
}
