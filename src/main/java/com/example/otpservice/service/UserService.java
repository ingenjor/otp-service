package com.example.otpservice.service;

import com.example.otpservice.dao.UserDao;
import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static UserService instance;
    private final UserDao userDao;
    private final JwtService jwtService;

    private UserService() {
        this.userDao = new UserDao();
        this.jwtService = new JwtService();
    }

    public static synchronized UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }

    public User register(String login, String password, UserRole role) throws Exception {
        if (userDao.findByLogin(login).isPresent()) {
            throw new IllegalArgumentException("Login already exists");
        }
        if (role == UserRole.ADMIN && userDao.existsAdmin()) {
            throw new IllegalArgumentException("Administrator already exists");
        }
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User(login, hashed, role);
        return userDao.save(user);
    }

    public String login(String login, String password) throws Exception {
        Optional<User> userOpt = userDao.findByLogin(login);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid login or password");
        }
        User user = userOpt.get();
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid login or password");
        }
        logger.info("User {} logged in", login);
        return jwtService.generateToken(user);
    }

    public User getUserFromToken(String token) throws Exception {
        var claims = jwtService.validateToken(token);
        int userId = claims.get("userId", Integer.class);
        Optional<User> userOpt = userDao.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        return userOpt.get();
    }

    public List<User> getAllNonAdminUsers() throws SQLException {
        return userDao.findAllNonAdmin();
    }

    public void deleteUser(int userId) throws SQLException {
        if (!userDao.deleteById(userId)) {
            throw new IllegalArgumentException("User not found or cannot be deleted");
        }
    }

    public void updateTelegramChatId(int userId, String chatId) throws SQLException {
        if (!userDao.updateTelegramChatId(userId, chatId)) {
            throw new IllegalArgumentException("User not found");
        }
    }

    public Optional<User> findById(int userId) throws SQLException {
        return userDao.findById(userId);
    }
}
