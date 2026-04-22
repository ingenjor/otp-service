package com.example.otpservice.sender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class FileOtpSender implements OtpSender {
    private static final Path FILE_PATH = Paths.get("otp_codes.txt").toAbsolutePath();

    @Override
    public void send(String destination, String code) throws IOException {
        String line = String.format("%s | %s | %s%n", Instant.now(), destination, code);
        Files.write(FILE_PATH, line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
