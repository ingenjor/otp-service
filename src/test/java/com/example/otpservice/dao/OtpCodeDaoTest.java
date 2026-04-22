package com.example.otpservice.dao;

import com.example.otpservice.model.OtpCode;
import com.example.otpservice.model.OtpStatus;
import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OtpCodeDaoTest {
    private static HikariDataSource dataSource;
    private OtpCodeDao otpCodeDao;
    private UserDao userDao;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:otpdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
            config.setUsername("sa");
            config.setPassword("");
            config.setMaximumPoolSize(5);
            dataSource = new HikariDataSource(config);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id SERIAL PRIMARY KEY,
                        login VARCHAR(50) UNIQUE NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
                        telegram_chat_id VARCHAR(50)
                    )
                """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS otp_codes (
                        id SERIAL PRIMARY KEY,
                        operation_id VARCHAR(100) NOT NULL,
                        code VARCHAR(20) NOT NULL,
                        user_id INT NOT NULL,
                        status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'USED')),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        expires_at TIMESTAMP NOT NULL,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                """);
            }
        }

        // Очистка
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM otp_codes");
            stmt.execute("DELETE FROM users");
        }

        userDao = new UserDao();
        var userDataSourceField = UserDao.class.getDeclaredField("dataSource");
        userDataSourceField.setAccessible(true);
        userDataSourceField.set(userDao, dataSource);

        otpCodeDao = new OtpCodeDao();
        var otpDataSourceField = OtpCodeDao.class.getDeclaredField("dataSource");
        otpDataSourceField.setAccessible(true);
        otpDataSourceField.set(otpCodeDao, dataSource);

        testUser = userDao.save(new User("test", "hash", UserRole.USER));
    }

    private OtpCode createTestCode() throws SQLException {
        OtpCode code = new OtpCode();
        code.setOperationId("op1");
        code.setCode("123456");
        code.setUserId(testUser.getId());
        code.setStatus(OtpStatus.ACTIVE);
        code.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return otpCodeDao.save(code);
    }

    @Test
    void saveAndFindActive_ShouldWork() throws SQLException {
        OtpCode code = createTestCode();
        assertNotNull(code.getId());

        Optional<OtpCode> found = otpCodeDao.findActiveByOperationAndUser("op1", testUser.getId());
        assertTrue(found.isPresent());
        assertEquals("123456", found.get().getCode());
    }

    @Test
    void updateStatus_ShouldChangeStatus() throws SQLException {
        OtpCode code = createTestCode();
        assertTrue(otpCodeDao.updateStatus(code.getId(), OtpStatus.USED));
        Optional<OtpCode> found = otpCodeDao.findActiveByOperationAndUser("op1", testUser.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void expireOldCodes_ShouldUpdateExpired() throws SQLException {
        OtpCode expired = new OtpCode();
        expired.setOperationId("op_exp");
        expired.setCode("111111");
        expired.setUserId(testUser.getId());
        expired.setStatus(OtpStatus.ACTIVE);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otpCodeDao.save(expired);

        createTestCode();

        int count = otpCodeDao.expireOldCodes();
        assertEquals(1, count);

        Optional<OtpCode> found = otpCodeDao.findActiveByOperationAndUser("op_exp", testUser.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void deleteByUserId_ShouldRemoveAllCodes() throws SQLException {
        createTestCode();
        createTestCode();
        int deleted = otpCodeDao.deleteByUserId(testUser.getId());
        assertEquals(2, deleted);
    }
}
