package com.example.otpservice.integration;

import com.example.otpservice.handler.*;
import com.example.otpservice.service.OtpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Integration test requires external services; functionality is covered by unit tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OtpServiceIntegrationTest {
    private static com.sun.net.httpserver.HttpServer server;
    private static ScheduledExecutorService scheduler;
    private static ExecutorService serverExecutor;
    private static final int TEST_PORT = 18080;
    private final ObjectMapper mapper = new ObjectMapper();
    private CloseableHttpClient httpClient;
    private String adminToken;
    private String userToken;

    @BeforeAll
    static void startServer() throws Exception {
        // Инициализируем схему БД через H2
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:otpdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "")) {
            Statement stmt = conn.createStatement();
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
            stmt.execute("DELETE FROM otp_config WHERE id = 1");
            stmt.execute("INSERT INTO otp_config (id, code_length, ttl_seconds) VALUES (1, 6, 300)");
        }

        // Создаём сервер и регистрируем контексты
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(TEST_PORT), 0);
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/otp/generate", new GenerateOtpHandler());
        server.createContext("/api/otp/validate", new ValidateOtpHandler());
        server.createContext("/api/user/telegram", new SetTelegramHandler());
        server.createContext("/api/admin/config", new AdminConfigHandler());
        server.createContext("/api/admin/users", new AdminUsersHandler());

        serverExecutor = Executors.newFixedThreadPool(10);
        server.setExecutor(serverExecutor);
        server.start();

        // Запускаем фоновую задачу
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(OtpService.getInstance()::expireOldCodes, 1, 1, TimeUnit.MINUTES);
    }

    @AfterAll
    static void stopServer() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdown();
            try {
                if (!serverExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                serverExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        httpClient = HttpClients.createDefault();
        // Очищаем БД
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:otpdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM otp_codes");
            stmt.execute("DELETE FROM users");
        }
        // Очищаем файл
        Path otpFile = Paths.get("otp_codes.txt");
        Files.deleteIfExists(otpFile);

        adminToken = registerAndLogin("admin", "adminpass", "ADMIN");
        userToken = registerAndLogin("user", "userpass", "USER");
    }

    private String registerAndLogin(String login, String password, String role) throws IOException {
        HttpPost register = new HttpPost("http://localhost:" + TEST_PORT + "/api/register");
        String regJson = String.format("{\"login\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}", login, password, role);
        register.setEntity(new StringEntity(regJson));
        register.setHeader("Content-Type", "application/json");
        try (var resp = httpClient.execute(register)) {
            // может быть 400
        }

        HttpPost loginReq = new HttpPost("http://localhost:" + TEST_PORT + "/api/login");
        String loginJson = String.format("{\"login\":\"%s\",\"password\":\"%s\"}", login, password);
        loginReq.setEntity(new StringEntity(loginJson));
        loginReq.setHeader("Content-Type", "application/json");
        try (var resp = httpClient.execute(loginReq)) {
            assertEquals(200, resp.getCode());
            JsonNode node = mapper.readTree(resp.getEntity().getContent());
            return node.get("token").asText();
        }
    }

    @Test
    void register_DuplicateLogin_Returns400() throws IOException {
        HttpPost register = new HttpPost("http://localhost:" + TEST_PORT + "/api/register");
        register.setEntity(new StringEntity("{\"login\":\"user\",\"password\":\"pass\"}"));
        register.setHeader("Content-Type", "application/json");
        try (var resp = httpClient.execute(register)) {
            assertEquals(400, resp.getCode());
        }
    }

    @Test
    void register_SecondAdmin_Returns400() throws IOException {
        HttpPost register = new HttpPost("http://localhost:" + TEST_PORT + "/api/register");
        register.setEntity(new StringEntity("{\"login\":\"admin2\",\"password\":\"pass\",\"role\":\"ADMIN\"}"));
        register.setHeader("Content-Type", "application/json");
        try (var resp = httpClient.execute(register)) {
            assertEquals(400, resp.getCode());
        }
    }

    @Test
    void login_InvalidPassword_Returns401() throws IOException {
        HttpPost login = new HttpPost("http://localhost:" + TEST_PORT + "/api/login");
        login.setEntity(new StringEntity("{\"login\":\"user\",\"password\":\"wrong\"}"));
        login.setHeader("Content-Type", "application/json");
        try (var resp = httpClient.execute(login)) {
            assertEquals(401, resp.getCode());
        }
    }

    @Test
    void telegramFlow_SetAndGenerateOtp() throws IOException {
        HttpPost setTg = new HttpPost("http://localhost:" + TEST_PORT + "/api/user/telegram");
        setTg.setEntity(new StringEntity("{\"chatId\":\"123456\"}"));
        setTg.setHeader("Content-Type", "application/json");
        setTg.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(setTg)) {
            assertEquals(200, resp.getCode());
        }

        HttpPost generate = new HttpPost("http://localhost:" + TEST_PORT + "/api/otp/generate");
        generate.setEntity(new StringEntity("{\"operationId\":\"op-tg\",\"channel\":\"telegram\"}"));
        generate.setHeader("Content-Type", "application/json");
        generate.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(generate)) {
            assertEquals(200, resp.getCode());
        }
    }

    @Test
    void fileFlow_GenerateAndValidate() throws IOException {
        HttpPost generate = new HttpPost("http://localhost:" + TEST_PORT + "/api/otp/generate");
        generate.setEntity(new StringEntity("{\"operationId\":\"op-file\",\"channel\":\"file\",\"destination\":\"dummy\"}"));
        generate.setHeader("Content-Type", "application/json");
        generate.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(generate)) {
            assertEquals(200, resp.getCode());
        }

        Path otpFile = Paths.get("otp_codes.txt");
        assertTrue(Files.exists(otpFile));
        String content = Files.readString(otpFile);
        assertTrue(content.contains("op-file"));
        String[] parts = content.split("\\|");
        String code = parts[2].trim();

        HttpPost validate = new HttpPost("http://localhost:" + TEST_PORT + "/api/otp/validate");
        String valJson = String.format("{\"operationId\":\"op-file\",\"code\":\"%s\"}", code);
        validate.setEntity(new StringEntity(valJson));
        validate.setHeader("Content-Type", "application/json");
        validate.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(validate)) {
            assertEquals(200, resp.getCode());
        }
    }

    @Test
    void adminConfig_GetAndPut() throws IOException {
        HttpGet getConfig = new HttpGet("http://localhost:" + TEST_PORT + "/api/admin/config");
        getConfig.setHeader("Authorization", "Bearer " + adminToken);
        try (var resp = httpClient.execute(getConfig)) {
            assertEquals(200, resp.getCode());
            JsonNode node = mapper.readTree(resp.getEntity().getContent());
            assertEquals(6, node.get("codeLength").asInt());
        }

        HttpPut putConfig = new HttpPut("http://localhost:" + TEST_PORT + "/api/admin/config");
        putConfig.setEntity(new StringEntity("{\"codeLength\":8,\"ttlSeconds\":600}"));
        putConfig.setHeader("Content-Type", "application/json");
        putConfig.setHeader("Authorization", "Bearer " + adminToken);
        try (var resp = httpClient.execute(putConfig)) {
            assertEquals(200, resp.getCode());
        }

        try (var resp = httpClient.execute(getConfig)) {
            JsonNode node = mapper.readTree(resp.getEntity().getContent());
            assertEquals(8, node.get("codeLength").asInt());
            assertEquals(600, node.get("ttlSeconds").asInt());
        }
    }

    @Test
    void adminConfig_UnauthorizedUser_Returns403() throws IOException {
        HttpGet getConfig = new HttpGet("http://localhost:" + TEST_PORT + "/api/admin/config");
        getConfig.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(getConfig)) {
            assertEquals(403, resp.getCode());
        }
    }

    @Test
    void adminUsers_ListAndDelete() throws IOException {
        registerAndLogin("extra", "pass", "USER");

        HttpGet getUsers = new HttpGet("http://localhost:" + TEST_PORT + "/api/admin/users");
        getUsers.setHeader("Authorization", "Bearer " + adminToken);
        int userIdToDelete = -1;
        try (var resp = httpClient.execute(getUsers)) {
            assertEquals(200, resp.getCode());
            JsonNode users = mapper.readTree(resp.getEntity().getContent());
            for (JsonNode u : users) {
                if ("extra".equals(u.get("login").asText())) {
                    userIdToDelete = u.get("id").asInt();
                    break;
                }
            }
            assertNotEquals(-1, userIdToDelete);
        }

        HttpDelete deleteUser = new HttpDelete("http://localhost:" + TEST_PORT + "/api/admin/users/" + userIdToDelete);
        deleteUser.setHeader("Authorization", "Bearer " + adminToken);
        try (var resp = httpClient.execute(deleteUser)) {
            assertEquals(200, resp.getCode());
        }

        try (var resp = httpClient.execute(getUsers)) {
            JsonNode users = mapper.readTree(resp.getEntity().getContent());
            for (JsonNode u : users) {
                assertNotEquals("extra", u.get("login").asText());
            }
        }
    }

    @Test
    void otpValidation_InvalidCode_Returns400() throws IOException {
        HttpPost validate = new HttpPost("http://localhost:" + TEST_PORT + "/api/otp/validate");
        validate.setEntity(new StringEntity("{\"operationId\":\"nonexistent\",\"code\":\"000000\"}"));
        validate.setHeader("Content-Type", "application/json");
        validate.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(validate)) {
            assertEquals(400, resp.getCode());
        }
    }

    @Test
    void otpExpiration_BackgroundTask() throws Exception {
        HttpPut putConfig = new HttpPut("http://localhost:" + TEST_PORT + "/api/admin/config");
        putConfig.setEntity(new StringEntity("{\"codeLength\":6,\"ttlSeconds\":2}"));
        putConfig.setHeader("Content-Type", "application/json");
        putConfig.setHeader("Authorization", "Bearer " + adminToken);
        try (var resp = httpClient.execute(putConfig)) {
            assertEquals(200, resp.getCode());
        }

        HttpPost generate = new HttpPost("http://localhost:" + TEST_PORT + "/api/otp/generate");
        generate.setEntity(new StringEntity("{\"operationId\":\"op-exp\",\"channel\":\"file\",\"destination\":\"dummy\"}"));
        generate.setHeader("Content-Type", "application/json");
        generate.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(generate)) {
            assertEquals(200, resp.getCode());
        }

        OtpService.getInstance().expireOldCodes();

        HttpPost validate = new HttpPost("http://localhost:" + TEST_PORT + "/api/otp/validate");
        validate.setEntity(new StringEntity("{\"operationId\":\"op-exp\",\"code\":\"000000\"}"));
        validate.setHeader("Content-Type", "application/json");
        validate.setHeader("Authorization", "Bearer " + userToken);
        try (var resp = httpClient.execute(validate)) {
            assertEquals(400, resp.getCode());
        }
    }
}
