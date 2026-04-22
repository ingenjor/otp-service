package com.example.otpservice.service;

import com.example.otpservice.dao.OtpConfigDao;
import com.example.otpservice.model.OtpConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {
    @Mock private OtpConfigDao configDao;
    private ConfigService configService;

    @BeforeEach
    void setUp() throws Exception {
        var constructor = ConfigService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        configService = constructor.newInstance();
        var field = ConfigService.class.getDeclaredField("configDao");
        field.setAccessible(true);
        field.set(configService, configDao);
    }

    @Test
    void getConfig_ShouldReturnConfig() throws SQLException {
        OtpConfig expected = new OtpConfig(8, 600);
        when(configDao.getConfig()).thenReturn(expected);
        OtpConfig actual = configService.getConfig();
        assertEquals(expected.getCodeLength(), actual.getCodeLength());
        assertEquals(expected.getTtlSeconds(), actual.getTtlSeconds());
    }

    @Test
    void updateConfig_ShouldCallDao_WhenValidParams() throws SQLException {
        configService.updateConfig(6, 300);
        verify(configDao).updateConfig(argThat(c -> c.getCodeLength() == 6 && c.getTtlSeconds() == 300));
    }

    @Test
    void updateConfig_ShouldThrow_WhenLengthTooSmall() {
        assertThrows(IllegalArgumentException.class, () -> configService.updateConfig(3, 300));
    }

    @Test
    void updateConfig_ShouldThrow_WhenTtlTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> configService.updateConfig(6, 4000));
    }
}
