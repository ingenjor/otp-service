package com.example.otpservice.service;

import com.example.otpservice.dao.OtpCodeDao;
import com.example.otpservice.dao.OtpConfigDao;
import com.example.otpservice.model.OtpCode;
import com.example.otpservice.model.OtpConfig;
import com.example.otpservice.model.OtpStatus;
import com.example.otpservice.sender.OtpSender;
import com.example.otpservice.sender.SenderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {
    @Mock private OtpCodeDao otpCodeDao;
    @Mock private OtpConfigDao otpConfigDao;
    @Mock private OtpSender otpSender;
    private OtpService otpService;

    @BeforeEach
    void setUp() throws Exception {
        otpService = OtpService.getInstance();
        var codeDaoField = OtpService.class.getDeclaredField("otpCodeDao");
        codeDaoField.setAccessible(true);
        codeDaoField.set(otpService, otpCodeDao);
        var configDaoField = OtpService.class.getDeclaredField("otpConfigDao");
        configDaoField.setAccessible(true);
        configDaoField.set(otpService, otpConfigDao);

        // lenient() позволяет избежать UnnecessaryStubbingException
        lenient().when(otpConfigDao.getConfig()).thenReturn(new OtpConfig(6, 300));
    }

    @Test
    void generateAndSend_ShouldSaveCodeAndCallSender() throws Exception {
        try (MockedStatic<SenderFactory> factoryMock = mockStatic(SenderFactory.class)) {
            factoryMock.when(() -> SenderFactory.getSender("email")).thenReturn(otpSender);
            when(otpCodeDao.save(any(OtpCode.class))).thenAnswer(inv -> {
                OtpCode c = inv.getArgument(0);
                c.setId(1);
                return c;
            });

            OtpCode result = otpService.generateAndSend("op1", 1, "email", "test@example.com", null);
            assertNotNull(result);
            assertEquals("op1", result.getOperationId());
            assertEquals(OtpStatus.ACTIVE, result.getStatus());
            verify(otpSender).send(eq("test@example.com"), anyString());
        }
    }

    @Test
    void generateAndSend_TelegramWithoutChatId_Throws() {
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                otpService.generateAndSend("op1", 1, "telegram", null, null)
        );
        assertTrue(exception.getMessage().contains("Telegram chat ID is required"));
    }

    @Test
    void validateCode_ShouldReturnTrue_WhenCodeValidAndNotExpired() throws SQLException {
        OtpCode activeCode = new OtpCode();
        activeCode.setId(1);
        activeCode.setCode("123456");
        activeCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        activeCode.setStatus(OtpStatus.ACTIVE);
        when(otpCodeDao.findActiveByOperationAndUser("op1", 1)).thenReturn(Optional.of(activeCode));
        when(otpCodeDao.updateStatus(1, OtpStatus.USED)).thenReturn(true);

        assertTrue(otpService.validateCode("op1", 1, "123456"));
        verify(otpCodeDao).updateStatus(1, OtpStatus.USED);
    }

    @Test
    void validateCode_ShouldReturnFalse_WhenCodeMismatch() throws SQLException {
        OtpCode activeCode = new OtpCode();
        activeCode.setId(1);
        activeCode.setCode("123456");
        activeCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(otpCodeDao.findActiveByOperationAndUser("op1", 1)).thenReturn(Optional.of(activeCode));

        assertFalse(otpService.validateCode("op1", 1, "000000"));
        verify(otpCodeDao, never()).updateStatus(anyInt(), any());
    }

    @Test
    void validateCode_ShouldExpireAndReturnFalse_WhenCodeExpired() throws SQLException {
        OtpCode expiredCode = new OtpCode();
        expiredCode.setId(1);
        expiredCode.setCode("123456");
        expiredCode.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(otpCodeDao.findActiveByOperationAndUser("op1", 1)).thenReturn(Optional.of(expiredCode));
        when(otpCodeDao.updateStatus(1, OtpStatus.EXPIRED)).thenReturn(true);

        assertFalse(otpService.validateCode("op1", 1, "123456"));
        verify(otpCodeDao).updateStatus(1, OtpStatus.EXPIRED);
    }

    @Test
    void expireOldCodes_ShouldCallDao() throws SQLException {
        when(otpCodeDao.expireOldCodes()).thenReturn(3);
        otpService.expireOldCodes();
        verify(otpCodeDao).expireOldCodes();
    }
}
