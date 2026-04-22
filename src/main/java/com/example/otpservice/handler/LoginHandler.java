package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.service.UserService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LoginHandler extends AuthFilter {
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
                sendErrorResponse(exchange, 400, "Missing login or password");
                return;
            }
            String login = json.get("login").asText();
            String password = json.get("password").asText();

            String token = userService.login(login, password);
            String response = String.format("{\"token\": \"%s\"}", token);
            sendResponse(exchange, 200, response);
            logger.info("User {} logged in", login);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            logger.error("Login error", e);
            sendErrorResponse(exchange, 500, "Login failed: " + e.getMessage());
        }
    }
}
