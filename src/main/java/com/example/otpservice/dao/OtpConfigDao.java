package com.example.otpservice.dao;

import com.example.otpservice.config.DatabaseConfig;
import com.example.otpservice.model.OtpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;

public class OtpConfigDao {
    private static final Logger logger = LoggerFactory.getLogger(OtpConfigDao.class);
    private final DataSource dataSource;

    public OtpConfigDao() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    public OtpConfig getConfig() throws SQLException {
        String sql = "SELECT code_length, ttl_seconds FROM otp_config WHERE id = 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new OtpConfig(rs.getInt("code_length"), rs.getInt("ttl_seconds"));
            }
        }
        OtpConfig defaultConfig = new OtpConfig(6, 300);
        updateConfig(defaultConfig);
        return defaultConfig;
    }

    public void updateConfig(OtpConfig config) throws SQLException {
        String sql = "INSERT INTO otp_config (id, code_length, ttl_seconds) VALUES (1, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET code_length = EXCLUDED.code_length, ttl_seconds = EXCLUDED.ttl_seconds";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, config.getCodeLength());
            stmt.setInt(2, config.getTtlSeconds());
            stmt.executeUpdate();
            logger.info("OTP config updated: length={}, ttl={}", config.getCodeLength(), config.getTtlSeconds());
        }
    }
}
