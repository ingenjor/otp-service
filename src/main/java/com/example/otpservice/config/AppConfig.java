package com.example.otpservice.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "application.properties";
    private static Properties properties = new Properties();

    static {
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new RuntimeException("Cannot find " + CONFIG_FILE);
            }
            properties.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public static String getDbUrl() { return properties.getProperty("db.url"); }
    public static String getDbUsername() { return properties.getProperty("db.username"); }
    public static String getDbPassword() { return properties.getProperty("db.password"); }
    public static int getDbPoolSize() { return Integer.parseInt(properties.getProperty("db.pool.size", "10")); }
    public static String getJwtSecret() { return properties.getProperty("jwt.secret"); }
    public static int getJwtExpirationSeconds() { return Integer.parseInt(properties.getProperty("jwt.expiration.seconds", "3600")); }
    public static int getServerPort() { return Integer.parseInt(properties.getProperty("server.port", "8080")); }
}
