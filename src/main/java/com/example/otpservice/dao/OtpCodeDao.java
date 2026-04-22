package com.example.otpservice.dao;

import com.example.otpservice.config.DatabaseConfig;
import com.example.otpservice.model.OtpCode;
import com.example.otpservice.model.OtpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

public class OtpCodeDao {
    private static final Logger logger = LoggerFactory.getLogger(OtpCodeDao.class);
    private final DataSource dataSource;

    public OtpCodeDao() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    public OtpCode save(OtpCode code) throws SQLException {
        String sql = "INSERT INTO otp_codes (operation_id, code, user_id, status, expires_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, code.getOperationId());
            stmt.setString(2, code.getCode());
            stmt.setInt(3, code.getUserId());
            stmt.setString(4, code.getStatus().name());
            stmt.setTimestamp(5, Timestamp.valueOf(code.getExpiresAt()));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    code.setId(keys.getInt(1));
                }
            }
            logger.debug("OTP code saved for operation: {}", code.getOperationId());
            return code;
        }
    }

    public Optional<OtpCode> findActiveByOperationAndUser(String operationId, int userId) throws SQLException {
        String sql = "SELECT id, operation_id, code, user_id, status, created_at, expires_at " +
                "FROM otp_codes WHERE operation_id = ? AND user_id = ? AND status = 'ACTIVE'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, operationId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean updateStatus(int id, OtpStatus status) throws SQLException {
        String sql = "UPDATE otp_codes SET status = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, id);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                logger.info("OTP code id={} updated to status {}", id, status);
            }
            return affected > 0;
        }
    }

    public int expireOldCodes() throws SQLException {
        String sql = "UPDATE otp_codes SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at < NOW()";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int count = stmt.executeUpdate(sql);
            if (count > 0) {
                logger.info("Expired {} OTP codes", count);
            }
            return count;
        }
    }

    public int deleteByUserId(int userId) throws SQLException {
        String sql = "DELETE FROM otp_codes WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            int count = stmt.executeUpdate();
            logger.info("Deleted {} OTP codes for user id={}", count, userId);
            return count;
        }
    }

    private OtpCode mapRow(ResultSet rs) throws SQLException {
        OtpCode code = new OtpCode();
        code.setId(rs.getInt("id"));
        code.setOperationId(rs.getString("operation_id"));
        code.setCode(rs.getString("code"));
        code.setUserId(rs.getInt("user_id"));
        code.setStatus(OtpStatus.valueOf(rs.getString("status")));
        code.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        code.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
        return code;
    }
}
