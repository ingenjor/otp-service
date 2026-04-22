package com.example.otpservice.dao;

import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserDaoTest {
    private static HikariDataSource dataSource;
    private UserDao userDao;

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
                    CREATE TABLE IF NOT EXISTS otp_config (
                        id INT PRIMARY KEY,
                        code_length INT NOT NULL DEFAULT 6,
                        ttl_seconds INT NOT NULL DEFAULT 300
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

        // Очистка таблиц перед каждым тестом
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM otp_codes");
            stmt.execute("DELETE FROM users");
        }

        userDao = new UserDao();
        var dataSourceField = UserDao.class.getDeclaredField("dataSource");
        dataSourceField.setAccessible(true);
        dataSourceField.set(userDao, dataSource);
    }

    @Test
    void saveAndFindById_ShouldWork() throws SQLException {
        User user = new User("john", "hash123", UserRole.USER);
        user = userDao.save(user);
        assertNotNull(user.getId());

        Optional<User> found = userDao.findById(user.getId());
        assertTrue(found.isPresent());
        assertEquals("john", found.get().getLogin());
    }

    @Test
    void findByLogin_ShouldReturnUser_WhenExists() throws SQLException {
        User user = new User("alice", "hash456", UserRole.ADMIN);
        userDao.save(user);

        Optional<User> found = userDao.findByLogin("alice");
        assertTrue(found.isPresent());
        assertEquals(UserRole.ADMIN, found.get().getRole());
    }

    @Test
    void existsAdmin_ShouldReturnTrue_WhenAdminExists() throws SQLException {
        assertFalse(userDao.existsAdmin());
        userDao.save(new User("admin", "hash", UserRole.ADMIN));
        assertTrue(userDao.existsAdmin());
    }

    @Test
    void findAllNonAdmin_ShouldReturnOnlyUsers() throws SQLException {
        userDao.save(new User("user1", "hash", UserRole.USER));
        userDao.save(new User("admin1", "hash", UserRole.ADMIN));
        userDao.save(new User("user2", "hash", UserRole.USER));

        var users = userDao.findAllNonAdmin();
        assertEquals(2, users.size());
        assertTrue(users.stream().allMatch(u -> u.getRole() == UserRole.USER));
    }

    @Test
    void deleteById_ShouldNotDeleteAdmin() throws SQLException {
        User admin = userDao.save(new User("admin", "hash", UserRole.ADMIN));
        boolean deleted = userDao.deleteById(admin.getId());
        assertFalse(deleted);
        assertTrue(userDao.findById(admin.getId()).isPresent());
    }

    @Test
    void deleteById_ShouldDeleteUser() throws SQLException {
        User user = userDao.save(new User("user", "hash", UserRole.USER));
        boolean deleted = userDao.deleteById(user.getId());
        assertTrue(deleted);
        assertFalse(userDao.findById(user.getId()).isPresent());
    }

    @Test
    void updateTelegramChatId_ShouldWork() throws SQLException {
        User user = userDao.save(new User("tguser", "hash", UserRole.USER));
        assertNull(user.getTelegramChatId());

        userDao.updateTelegramChatId(user.getId(), "123456789");
        Optional<User> updated = userDao.findById(user.getId());
        assertEquals("123456789", updated.get().getTelegramChatId());
    }
}
