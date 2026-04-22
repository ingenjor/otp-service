package com.example.otpservice.service;

import com.example.otpservice.dao.OtpCodeDao;
import com.example.otpservice.dao.OtpConfigDao;
import com.example.otpservice.model.OtpCode;
import com.example.otpservice.model.OtpConfig;
import com.example.otpservice.model.OtpStatus;
import com.example.otpservice.sender.OtpSender;
import com.example.otpservice.sender.SenderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

public class OtpService {
    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static OtpService instance;
    private final OtpCodeDao otpCodeDao;
    private final OtpConfigDao otpConfigDao;
    private final SecureRandom random = new SecureRandom();

    private OtpService() {
        this.otpCodeDao = new OtpCodeDao();
        this.otpConfigDao = new OtpConfigDao();
    }

    public static synchronized OtpService getInstance() {
        if (instance == null) {
            instance = new OtpService();
        }
        return instance;
    }

    /**
     * Генерирует и отправляет OTP-код.
     * @param operationId идентификатор операции
     * @param userId ID пользователя
     * @param channel канал (email, sms, telegram, file)
     * @param destination адрес (email или телефон). Для Telegram можно передать null (используется сохранённый chat_id)
     * @param telegramChatId опционально chat_id Telegram (если не сохранён)
     */
    public OtpCode generateAndSend(String operationId, int userId, String channel, String destination, String telegramChatId) throws Exception {
        OtpConfig config = otpConfigDao.getConfig();
        String code = generateCode(config.getCodeLength());
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(config.getTtlSeconds());

        OtpCode otpCode = new OtpCode();
        otpCode.setOperationId(operationId);
        otpCode.setCode(code);
        otpCode.setUserId(userId);
        otpCode.setStatus(OtpStatus.ACTIVE);
        otpCode.setExpiresAt(expiresAt);
        otpCode = otpCodeDao.save(otpCode);

        try {
            OtpSender sender = SenderFactory.getSender(channel);
            if ("telegram".equalsIgnoreCase(channel)) {
                // Для Telegram используем переданный chatId или из настроек (если передан null, значит используем сохранённый)
                if (telegramChatId == null) {
                    throw new IllegalArgumentException("Telegram chat ID is required");
                }
                sender.send(telegramChatId, code);
                logger.info("OTP code sent via Telegram to chatId {} for operation {}", telegramChatId, operationId);
            } else {
                sender.send(destination, code);
                logger.info("OTP code sent via {} to {} for operation {}", channel, destination, operationId);
            }
        } catch (Exception e) {
            logger.error("Failed to send OTP via {}", channel, e);
            throw new RuntimeException("Failed to send OTP: " + e.getMessage(), e);
        }

        return otpCode;
    }

    public boolean validateCode(String operationId, int userId, String code) throws SQLException {
        Optional<OtpCode> opt = otpCodeDao.findActiveByOperationAndUser(operationId, userId);
        if (opt.isEmpty()) {
            logger.warn("No active OTP for operation {} and user {}", operationId, userId);
            return false;
        }
        OtpCode otpCode = opt.get();
        if (LocalDateTime.now().isAfter(otpCode.getExpiresAt())) {
            otpCodeDao.updateStatus(otpCode.getId(), OtpStatus.EXPIRED);
            logger.info("OTP code expired for operation {}", operationId);
            return false;
        }
        if (!otpCode.getCode().equals(code)) {
            logger.warn("Invalid OTP code attempt for operation {}", operationId);
            return false;
        }
        otpCodeDao.updateStatus(otpCode.getId(), OtpStatus.USED);
        logger.info("OTP code validated successfully for operation {}", operationId);
        return true;
    }

    private String generateCode(int length) {
        int min = (int) Math.pow(10, length - 1);
        int max = (int) Math.pow(10, length) - 1;
        int num = random.nextInt(max - min + 1) + min;
        return String.valueOf(num);
    }

    public void expireOldCodes() {
        try {
            int expired = otpCodeDao.expireOldCodes();
            if (expired > 0) {
                logger.info("Background task expired {} OTP codes", expired);
            }
        } catch (SQLException e) {
            logger.error("Failed to expire old OTP codes", e);
        }
    }

    public void deleteCodesForUser(int userId) throws SQLException {
        otpCodeDao.deleteByUserId(userId);
    }
}
