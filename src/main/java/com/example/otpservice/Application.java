package com.example.otpservice;

import com.example.otpservice.config.AppConfig;
import com.example.otpservice.handler.*;
import com.example.otpservice.service.OtpService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Application {
    public static void main(String[] args) throws IOException {
        int port = AppConfig.getServerPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Public endpoints
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());

        // Protected user endpoints
        server.createContext("/api/otp/generate", new GenerateOtpHandler());
        server.createContext("/api/otp/validate", new ValidateOtpHandler());
        server.createContext("/api/user/telegram", new SetTelegramHandler());

        // Admin endpoints
        server.createContext("/api/admin/config", new AdminConfigHandler());
        server.createContext("/api/admin/users", new AdminUsersHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        // Start background OTP expiration task
        OtpService otpService = OtpService.getInstance();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(otpService::expireOldCodes, 1, 1, TimeUnit.MINUTES);

        System.out.println("OTP Service started on port " + port);
    }
}
