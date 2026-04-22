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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetTelegramHandlerTest {
    @Mock private UserService userService;
    @Mock private HttpExchange exchange;
    private SetTelegramHandler handler;
    private ByteArrayOutputStream responseBody;
    private Headers headers;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SetTelegramHandler();
        var field = SetTelegramHandler.class.getDeclaredField("userService");
        field.setAccessible(true);
        field.set(handler, userService);

        testUser = new User("user", "hash", UserRole.USER);
        testUser.setId(1);

        responseBody = new ByteArrayOutputStream();
        headers = new Headers();

        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestMethod()).thenReturn("POST");
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/user/telegram"));
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getRequestHeaders()).thenReturn(new Headers());
    }

    @Test
    void setTelegram_Success() throws Exception {
        String json = "{\"chatId\":\"987654321\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(userService).updateTelegramChatId(1, "987654321");
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void setTelegram_MissingChatId_Returns400() throws Exception {
        String json = "{}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void setTelegram_UserNotFound_Returns400() throws Exception {
        String json = "{\"chatId\":\"123\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes()));
        doThrow(new IllegalArgumentException("User not found")).when(userService).updateTelegramChatId(1, "123");

        handler.handleAuthenticated(exchange, testUser);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }
}
