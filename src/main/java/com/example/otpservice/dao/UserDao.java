package com.example.otpservice.dao;

import com.example.otpservice.config.DatabaseConfig;
import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    private final DataSource dataSource;

    public UserDao() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    public Optional<User> findByLogin(String login) throws SQLException {
        String sql = "SELECT id, login, password_hash, role, telegram_chat_id FROM users WHERE login = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, login);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(int id) throws SQLException {
        String sql = "SELECT id, login, password_hash, role, telegram_chat_id FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean existsAdmin() throws SQLException {
        String sql = "SELECT 1 FROM users WHERE role = 'ADMIN' LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    public User save(User user) throws SQLException {
        String sql = "INSERT INTO users (login, password_hash, role, telegram_chat_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getLogin());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getRole().name());
            stmt.setString(4, user.getTelegramChatId());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            logger.info("User saved: {}", user.getLogin());
            return user;
        }
    }

    public boolean updateTelegramChatId(int userId, String chatId) throws SQLException {
        String sql = "UPDATE users SET telegram_chat_id = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, chatId);
            stmt.setInt(2, userId);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                logger.info("Updated telegram_chat_id for user id={}", userId);
            }
            return affected > 0;
        }
    }

    public List<User> findAllNonAdmin() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, login, password_hash, role, telegram_chat_id FROM users WHERE role = 'USER'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    public boolean deleteById(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ? AND role != 'ADMIN'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                logger.info("User with id {} deleted", id);
                return true;
            }
            return false;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setLogin(rs.getString("login"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(UserRole.valueOf(rs.getString("role")));
        user.setTelegramChatId(rs.getString("telegram_chat_id"));
        return user;
    }
}
