package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.example.otpservice.service.OtpService;
import com.example.otpservice.service.UserService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class AdminUsersHandler extends AuthFilter {
    private final UserService userService = UserService.getInstance();
    private final OtpService otpService = OtpService.getInstance();

    @Override
    protected void handleAuthenticated(HttpExchange exchange, User user) throws IOException {
        if (user.getRole() != UserRole.ADMIN) {
            sendErrorResponse(exchange, 403, "Admin access required");
            return;
        }

        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method)) {
                List<User> users = userService.getAllNonAdminUsers();
                ArrayNode array = JsonUtil.mapper.createArrayNode();
                for (User u : users) {
                    ObjectNode node = JsonUtil.mapper.createObjectNode();
                    node.put("id", u.getId());
                    node.put("login", u.getLogin());
                    node.put("role", u.getRole().toString());
                    node.put("telegramChatId", u.getTelegramChatId() != null ? u.getTelegramChatId() : "");
                    array.add(node);
                }
                sendResponse(exchange, 200, JsonUtil.toJson(array));
                logger.info("Admin {} fetched user list", user.getLogin());
            } else if ("DELETE".equals(method)) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length < 4) {
                    sendErrorResponse(exchange, 400, "User ID required");
                    return;
                }
                int userId;
                try {
                    userId = Integer.parseInt(parts[parts.length - 1]);
                } catch (NumberFormatException e) {
                    sendErrorResponse(exchange, 400, "Invalid user ID");
                    return;
                }

                // Получаем пользователя и проверяем, что он не администратор
                Optional<User> targetOpt = userService.findById(userId);
                if (targetOpt.isEmpty()) {
                    sendErrorResponse(exchange, 404, "User not found");
                    return;
                }
                User target = targetOpt.get();
                if (target.getRole() == UserRole.ADMIN) {
                    sendErrorResponse(exchange, 403, "Cannot delete administrator");
                    return;
                }

                otpService.deleteCodesForUser(userId);
                userService.deleteUser(userId);
                sendResponse(exchange, 200, "{\"status\": \"success\"}");
                logger.info("Admin {} deleted user id={}", user.getLogin(), userId);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Admin users operation failed", e);
            sendErrorResponse(exchange, 500, "Operation failed: " + e.getMessage());
        }
    }
}
