package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.model.OtpConfig;
import com.example.otpservice.service.ConfigService;
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
class AdminConfigHandlerTest {
    @Mock private ConfigService configService;
    @Mock private HttpExchange exchange;
    private AdminConfigHandler handler;
    private ByteArrayOutputStream responseBody;
    private Headers headers;
    private User adminUser;

    @BeforeEach
    void setUp() throws Exception {
        handler = new AdminConfigHandler();
        var field = AdminConfigHandler.class.getDeclaredField("configService");
        field.setAccessible(true);
        field.set(handler, configService);

        adminUser = new User("admin", "hash", UserRole.ADMIN);
        adminUser.setId(1);

        responseBody = new ByteArrayOutputStream();
        headers = new Headers();

        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/admin/config"));
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getRequestHeaders()).thenReturn(new Headers());
    }

    @Test
    void getConfig_Success() throws Exception {
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(configService.getConfig()).thenReturn(new OtpConfig(8, 600));

        handler.handleAuthenticated(exchange, adminUser);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String resp = responseBody.toString();
        assertTrue(resp.contains("8") && resp.contains("600"));
    }

    @Test
    void putConfig_Success() throws Exception {
        when(exchange.getRequestMethod()).thenReturn("PUT");
        String json = "{\"codeLength\":7,\"ttlSeconds\":500}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, adminUser);

        verify(configService).updateConfig(7, 500);
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void putConfig_InvalidRange_Returns400() throws Exception {
        when(exchange.getRequestMethod()).thenReturn("PUT");
        String json = "{\"codeLength\":3,\"ttlSeconds\":100}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        doThrow(new IllegalArgumentException("Code length must be between 4 and 10"))
                .when(configService).updateConfig(3, 100);

        handler.handleAuthenticated(exchange, adminUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void nonAdmin_Returns403() throws Exception {
        User normalUser = new User("user", "hash", UserRole.USER);
        handler.handleAuthenticated(exchange, normalUser);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
    }
}
