package com.example.otpservice.service;

import com.example.otpservice.dao.OtpConfigDao;
import com.example.otpservice.model.OtpConfig;

import java.sql.SQLException;

public class ConfigService {
    private static ConfigService instance;
    private final OtpConfigDao configDao;

    private ConfigService() {
        this.configDao = new OtpConfigDao();
    }

    public static synchronized ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }

    public OtpConfig getConfig() throws SQLException {
        return configDao.getConfig();
    }

    public void updateConfig(int codeLength, int ttlSeconds) throws SQLException {
        if (codeLength < 4 || codeLength > 10) {
            throw new IllegalArgumentException("Code length must be between 4 and 10");
        }
        if (ttlSeconds < 30 || ttlSeconds > 3600) {
            throw new IllegalArgumentException("TTL must be between 30 and 3600 seconds");
        }
        OtpConfig config = new OtpConfig(codeLength, ttlSeconds);
        configDao.updateConfig(config);
    }
}
