package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.OtpService;
import com.example.otpservice.service.UserService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUsersHandlerTest {
    @Mock private UserService userService;
    @Mock private OtpService otpService;
    @Mock private HttpExchange exchange;
    private AdminUsersHandler handler;
    private ByteArrayOutputStream responseBody;
    private Headers headers;
    private User adminUser;

    @BeforeEach
    void setUp() throws Exception {
        handler = new AdminUsersHandler();
        var userField = AdminUsersHandler.class.getDeclaredField("userService");
        userField.setAccessible(true);
        userField.set(handler, userService);
        var otpField = AdminUsersHandler.class.getDeclaredField("otpService");
        otpField.setAccessible(true);
        otpField.set(handler, otpService);

        adminUser = new User("admin", "hash", UserRole.ADMIN);
        adminUser.setId(1);

        responseBody = new ByteArrayOutputStream();
        headers = new Headers();

        lenient().when(exchange.getResponseHeaders()).thenReturn(headers);
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create("/api/admin/users"));
        lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        lenient().when(exchange.getRequestHeaders()).thenReturn(new Headers());
    }

    @Test
    void getUsers_Success() throws Exception {
        when(exchange.getRequestMethod()).thenReturn("GET");
        User user1 = new User("user1", "h", UserRole.USER); user1.setId(1);
        User user2 = new User("user2", "h", UserRole.USER); user2.setId(2);
        when(userService.getAllNonAdminUsers()).thenReturn(List.of(user1, user2));

        handler.handleAuthenticated(exchange, adminUser);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String resp = responseBody.toString();
        assertTrue(resp.contains("user1") && resp.contains("user2"));
    }

    @Test
    void deleteUser_Success() throws Exception {
        when(exchange.getRequestMethod()).thenReturn("DELETE");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/admin/users/5"));
        User target = new User("user", "h", UserRole.USER); target.setId(5);
        when(userService.findById(5)).thenReturn(Optional.of(target));
        doNothing().when(userService).deleteUser(5);

        handler.handleAuthenticated(exchange, adminUser);

        verify(userService).deleteUser(5);
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void deleteUser_AdminOrNotFound_Returns404() throws Exception {
        when(exchange.getRequestMethod()).thenReturn("DELETE");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/admin/users/99"));
        when(userService.findById(99)).thenReturn(Optional.empty());

        handler.handleAuthenticated(exchange, adminUser);

        verify(exchange).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void nonAdmin_Returns403() throws Exception {
        User normalUser = new User("user", "hash", UserRole.USER);
        handler.handleAuthenticated(exchange, normalUser);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
    }
}
