package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.ConfigService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AdminConfigHandler extends AuthFilter {
    private final ConfigService configService = ConfigService.getInstance();

    @Override
    protected void handleAuthenticated(HttpExchange exchange, User user) throws IOException {
        if (user.getRole() != UserRole.ADMIN) {
            sendErrorResponse(exchange, 403, "Admin access required");
            return;
        }

        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method)) {
                var config = configService.getConfig();
                String response = String.format("{\"codeLength\": %d, \"ttlSeconds\": %d}",
                        config.getCodeLength(), config.getTtlSeconds());
                sendResponse(exchange, 200, response);
            } else if ("PUT".equals(method) || "POST".equals(method)) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = JsonUtil.mapper.readTree(requestBody);
                if (!json.has("codeLength") || !json.has("ttlSeconds")) {
                    sendErrorResponse(exchange, 400, "Missing fields: codeLength, ttlSeconds");
                    return;
                }
                int length = json.get("codeLength").asInt();
                int ttl = json.get("ttlSeconds").asInt();
                configService.updateConfig(length, ttl);
                sendResponse(exchange, 200, "{\"status\": \"success\"}");
                logger.info("Admin {} updated OTP config: length={}, ttl={}", user.getLogin(), length, ttl);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            logger.error("Admin config error", e);
            sendErrorResponse(exchange, 500, "Config update failed: " + e.getMessage());
        }
    }
}
