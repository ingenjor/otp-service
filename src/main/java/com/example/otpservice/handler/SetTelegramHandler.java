package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.service.UserService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Позволяет пользователю привязать свой Telegram chat_id.
 * POST /api/user/telegram
 * Body: { "chatId": "123456789" }
 */
public class SetTelegramHandler extends AuthFilter {
    private final UserService userService = UserService.getInstance();

    @Override
    protected void handleAuthenticated(HttpExchange exchange, User user) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = JsonUtil.mapper.readTree(requestBody);
            if (!json.has("chatId")) {
                sendErrorResponse(exchange, 400, "Missing 'chatId' field");
                return;
            }
            String chatId = json.get("chatId").asText();
            userService.updateTelegramChatId(user.getId(), chatId);
            sendResponse(exchange, 200, "{\"status\": \"success\"}");
            logger.info("User {} set Telegram chatId {}", user.getLogin(), chatId);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to set Telegram chatId", e);
            sendErrorResponse(exchange, 500, "Internal error: " + e.getMessage());
        }
    }
}
