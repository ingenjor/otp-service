package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.OtpService;
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
class ValidateOtpHandlerTest {
    @Mock private OtpService otpService;
    @Mock private HttpExchange exchange;
    private ValidateOtpHandler handler;
    private ByteArrayOutputStream responseBody;
    private Headers headers;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        handler = new ValidateOtpHandler();
        var field = ValidateOtpHandler.class.getDeclaredField("otpService");
        field.setAccessible(true);
        field.set(handler, otpService);

        testUser = new User("user", "hash", UserRole.USER);
        testUser.setId(1);

        responseBody = new ByteArrayOutputStream();
        headers = new Headers();

        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestMethod()).thenReturn("POST");
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/otp/validate"));
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getRequestHeaders()).thenReturn(new Headers());
    }

    @Test
    void validate_Success() throws Exception {
        String json = "{\"operationId\":\"op1\",\"code\":\"123456\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(otpService.validateCode("op1", 1, "123456")).thenReturn(true);

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertTrue(responseBody.toString().contains("success"));
    }

    @Test
    void validate_InvalidCode_Returns400() throws Exception {
        String json = "{\"operationId\":\"op1\",\"code\":\"000000\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        when(otpService.validateCode("op1", 1, "000000")).thenReturn(false);

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void validate_MissingFields_Returns400() throws Exception {
        String json = "{\"operationId\":\"op1\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }
}
