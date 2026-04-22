package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.UserService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RegisterHandler extends AuthFilter {
    private final UserService userService = UserService.getInstance();

    @Override
    protected User authenticate(HttpExchange exchange) {
        return null;
    }

    @Override
    protected void handleAuthenticated(HttpExchange exchange, User user) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = JsonUtil.mapper.readTree(requestBody);

            if (!json.has("login") || !json.has("password")) {
                sendErrorResponse(exchange, 400, "Missing required fields: login, password");
                return;
            }

            String login = json.get("login").asText();
            String password = json.get("password").asText();
            String roleStr = json.has("role") ? json.get("role").asText().toUpperCase() : "USER";

            UserRole role;
            try {
                role = UserRole.valueOf(roleStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(exchange, 400, "Invalid role. Allowed: USER, ADMIN");
                return;
            }

            User registered = userService.register(login, password, role);
            String response = String.format("{\"id\": %d, \"login\": \"%s\", \"role\": \"%s\"}",
                    registered.getId(), registered.getLogin(), registered.getRole());
            sendResponse(exchange, 201, response);
            logger.info("New user registered: {}", login);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            logger.error("Registration error", e);
            sendErrorResponse(exchange, 500, "Registration failed: " + e.getMessage());
        }
    }
}
