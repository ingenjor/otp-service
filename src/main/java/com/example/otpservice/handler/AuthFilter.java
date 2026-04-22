package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.service.JwtService;
import com.example.otpservice.service.UserService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class AuthFilter implements HttpHandler {
    protected static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);
    protected final JwtService jwtService;
    protected final UserService userService;

    public AuthFilter() {
        this.jwtService = new JwtService();
        this.userService = UserService.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String remoteAddr = exchange.getRemoteAddress().getAddress().getHostAddress();
        logger.info("Request: {} {} from {}", method, path, remoteAddr);

        try {
            User user = authenticate(exchange);
            // Вызываем handleAuthenticated даже если user == null
            // Публичные эндпоинты (register, login) ожидают null и сами отправляют ответ
            handleAuthenticated(exchange, user);
        } catch (Exception e) {
            logger.error("Unhandled exception in handler", e);
            sendErrorResponse(exchange, 500, "Internal server error");
        } finally {
            // Гарантированно закрываем соединение
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    protected User authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Для публичных эндпоинтов просто возвращаем null, ошибку не отправляем
            return null;
        }
        String token = authHeader.substring(7);
        try {
            return userService.getUserFromToken(token);
        } catch (ExpiredJwtException e) {
            logger.warn("Expired JWT token: {}", e.getMessage());
            sendErrorResponse(exchange, 401, "Token expired");
        } catch (Exception e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
            sendErrorResponse(exchange, 401, "Invalid token");
        }
        return null;
    }

    protected abstract void handleAuthenticated(HttpExchange exchange, User user) throws IOException;

    protected void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        logger.debug("Sending response: status={}, body={}", statusCode, response);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
        logger.debug("Response sent successfully");
    }

    protected void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = String.format("{\"error\": \"%s\"}", message);
        sendResponse(exchange, statusCode, json);
    }
}
