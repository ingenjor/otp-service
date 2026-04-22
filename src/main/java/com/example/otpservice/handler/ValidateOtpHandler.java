package com.example.otpservice.handler;

import com.example.otpservice.model.User;
import com.example.otpservice.service.OtpService;
import com.example.otpservice.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ValidateOtpHandler extends AuthFilter {
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
            if (!json.has("operationId") || !json.has("code")) {
                sendErrorResponse(exchange, 400, "Missing operationId or code");
                return;
            }
            String operationId = json.get("operationId").asText();
            String code = json.get("code").asText();

            boolean valid = otpService.validateCode(operationId, user.getId(), code);
            if (valid) {
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"OTP is valid\"}");
                logger.info("User {} successfully validated OTP for operation {}", user.getLogin(), operationId);
            } else {
                sendErrorResponse(exchange, 400, "Invalid or expired OTP");
                logger.warn("User {} failed OTP validation for operation {}", user.getLogin(), operationId);
            }
        } catch (Exception e) {
            logger.error("Validation error", e);
            sendErrorResponse(exchange, 500, "Validation failed: " + e.getMessage());
        }
    }
}
