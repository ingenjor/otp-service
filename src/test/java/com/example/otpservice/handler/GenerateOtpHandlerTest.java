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
class GenerateOtpHandlerTest {
    @Mock private OtpService otpService;
    @Mock private HttpExchange exchange;
    private GenerateOtpHandler handler;
    private ByteArrayOutputStream responseBody;
    private Headers headers;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GenerateOtpHandler();
        var field = GenerateOtpHandler.class.getDeclaredField("otpService");
        field.setAccessible(true);
        field.set(handler, otpService);

        testUser = new User("user", "hash", UserRole.USER);
        testUser.setId(1);
        testUser.setTelegramChatId("123456789");

        responseBody = new ByteArrayOutputStream();
        headers = new Headers();

        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestMethod()).thenReturn("POST");
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/otp/generate"));
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getRequestHeaders()).thenReturn(new Headers());
    }

    @Test
    void generate_Email_Success() throws Exception {
        String json = "{\"operationId\":\"op1\",\"channel\":\"email\",\"destination\":\"test@example.com\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(otpService).generateAndSend(eq("op1"), eq(1), eq("email"), eq("test@example.com"), isNull());
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        assertTrue(responseBody.toString().contains("success"));
    }

    @Test
    void generate_Telegram_Success() throws Exception {
        String json = "{\"operationId\":\"op1\",\"channel\":\"telegram\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(otpService).generateAndSend(eq("op1"), eq(1), eq("telegram"), isNull(), eq("123456789"));
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void generate_Telegram_NoChatId_Returns400() throws Exception {
        testUser.setTelegramChatId(null);
        String json = "{\"operationId\":\"op1\",\"channel\":\"telegram\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void generate_MissingOperationId_Returns400() throws Exception {
        String json = "{\"channel\":\"email\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }
}
