package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.service.OtpService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GenerateOtpHandler extends AuthFilter {
    private final OtpService otpService = OtpService.getInstance();

    @Override
    protected void handleAuthenticated(HttpExchange exchange, User user) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = JsonUtil.mapper.readTree(requestBody);
            if (!json.has("operationId") || !json.has("channel")) {
                sendErrorResponse(exchange, 400, "Missing required fields: operationId, channel");
                return;
            }
            String operationId = json.get("operationId").asText();
            String channel = json.get("channel").asText().toLowerCase();
            String destination = json.has("destination") ? json.get("destination").asText() : null;

            // Для Telegram используем сохранённый chat_id пользователя
            if ("telegram".equals(channel)) {
                if (user.getTelegramChatId() == null || user.getTelegramChatId().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Telegram chatId not set. Please set it via /api/user/telegram first.");
                    return;
                }
                otpService.generateAndSend(operationId, user.getId(), channel, null, user.getTelegramChatId());
            } else {
                if (destination == null || destination.isEmpty()) {
                    sendErrorResponse(exchange, 400, "Destination is required for channel " + channel);
                    return;
                }
                otpService.generateAndSend(operationId, user.getId(), channel, destination, null);
            }

            String response = String.format("{\"status\": \"success\", \"operationId\": \"%s\"}", operationId);
            sendResponse(exchange, 200, response);
            logger.info("OTP generated for user {} (op: {}, channel: {})", user.getLogin(), operationId, channel);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to generate OTP", e);
            sendErrorResponse(exchange, 500, "Failed to generate OTP: " + e.getMessage());
        }
    }
}
