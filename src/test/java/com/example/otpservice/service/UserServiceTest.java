package com.example.otpservice.service;

import com.example.otpservice.dao.UserDao;
import com.example.otpservice.model.User;
import com.example.otpservice.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserDao userDao;
    @Mock private JwtService jwtService;
    private UserService userService;

    @BeforeEach
    void setUp() throws Exception {
        var constructor = UserService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        userService = constructor.newInstance();
        var userDaoField = UserService.class.getDeclaredField("userDao");
        userDaoField.setAccessible(true);
        userDaoField.set(userService, userDao);
        var jwtField = UserService.class.getDeclaredField("jwtService");
        jwtField.setAccessible(true);
        jwtField.set(userService, jwtService);
    }

    @Test
    void register_Success() throws Exception {
        when(userDao.findByLogin("new")).thenReturn(Optional.empty());
        // existsAdmin не вызывается для USER, поэтому заглушка не нужна
        when(userDao.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1);
            return u;
        });

        User u = userService.register("new", "pass", UserRole.USER);
        assertNotNull(u.getId());
        verify(userDao).save(any());
    }

    @Test
    void register_SecondAdmin_Throws() throws SQLException {
        when(userDao.findByLogin("admin2")).thenReturn(Optional.empty());
        when(userDao.existsAdmin()).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("admin2", "pass", UserRole.ADMIN));
    }

    @Test
    void login_ValidCredentials_ReturnsToken() throws Exception {
        User user = new User("user", BCrypt.hashpw("pass", BCrypt.gensalt()), UserRole.USER);
        user.setId(1);
        when(userDao.findByLogin("user")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("token");

        String token = userService.login("user", "pass");
        assertEquals("token", token);
    }

    @Test
    void login_InvalidPassword_Throws() throws Exception {
        User user = new User("user", BCrypt.hashpw("pass", BCrypt.gensalt()), UserRole.USER);
        when(userDao.findByLogin("user")).thenReturn(Optional.of(user));
        assertThrows(IllegalArgumentException.class,
                () -> userService.login("user", "wrong"));
    }

    @Test
    void deleteUser_NotFound_Throws() throws SQLException {
        when(userDao.deleteById(999)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(999));
    }

    @Test
    void updateTelegramChatId_Success() throws SQLException {
        when(userDao.updateTelegramChatId(1, "123")).thenReturn(true);
        assertDoesNotThrow(() -> userService.updateTelegramChatId(1, "123"));
    }
}
