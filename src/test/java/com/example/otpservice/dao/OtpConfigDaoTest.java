package com.example.otpservice.dao;

import com.example.otpservice.model.OtpConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class OtpConfigDaoTest {
    private static HikariDataSource dataSource;
    private OtpConfigDao configDao;

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
                    CREATE TABLE IF NOT EXISTS otp_config (
                        id INT PRIMARY KEY,
                        code_length INT NOT NULL DEFAULT 6,
                        ttl_seconds INT NOT NULL DEFAULT 300
                    )
                """);
            }
        }
        // Очистка и вставка начального значения
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM otp_config WHERE id = 1");
            stmt.execute("INSERT INTO otp_config (id, code_length, ttl_seconds) VALUES (1, 6, 300)");
        }

        configDao = new OtpConfigDao();
        var dataSourceField = OtpConfigDao.class.getDeclaredField("dataSource");
        dataSourceField.setAccessible(true);
        dataSourceField.set(configDao, dataSource);
    }

    @Test
    void getConfig_ShouldReturnDefaultIfNotSet() throws SQLException {
        OtpConfig config = configDao.getConfig();
        assertEquals(6, config.getCodeLength());
        assertEquals(300, config.getTtlSeconds());
    }

    @Test
    void updateConfig_ShouldPersistChanges() throws SQLException {
        String mergeSql = """
            MERGE INTO otp_config (id, code_length, ttl_seconds)
            KEY(id) VALUES (1, ?, ?)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(mergeSql)) {
            stmt.setInt(1, 8);
            stmt.setInt(2, 600);
            stmt.executeUpdate();
        }

        OtpConfig config = configDao.getConfig();
        assertEquals(8, config.getCodeLength());
        assertEquals(600, config.getTtlSeconds());
    }
}
