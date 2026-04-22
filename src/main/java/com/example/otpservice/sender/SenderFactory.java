package com.example.otpservice.sender;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SenderFactory {
    public static OtpSender getSender(String channel) throws Exception {
        return switch (channel.toLowerCase()) {
            case "email" -> createEmailSender();
            case "sms" -> createSmppSender();
            case "telegram" -> createTelegramSender();
            case "file" -> new FileOtpSender();
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }

    private static EmailOtpSender createEmailSender() throws IOException {
        Properties props = new Properties();
        try (InputStream is = SenderFactory.class.getClassLoader().getResourceAsStream("email.properties")) {
            props.load(is);
        }
        return new EmailOtpSender(props);
    }

    private static SmppOtpSender createSmppSender() throws IOException {
        Properties props = new Properties();
        try (InputStream is = SenderFactory.class.getClassLoader().getResourceAsStream("sms.properties")) {
            props.load(is);
        }
        return new SmppOtpSender(props);
    }

    private static TelegramOtpSender createTelegramSender() throws IOException {
        Properties props = new Properties();
        try (InputStream is = SenderFactory.class.getClassLoader().getResourceAsStream("telegram.properties")) {
            props.load(is);
        }
        String botToken = props.getProperty("telegram.bot.token");
        return new TelegramOtpSender(botToken);
    }
}
