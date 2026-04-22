package com.example.otpservice.sender;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailOtpSender implements OtpSender {
    private final Session session;
    private final String fromEmail;

    public EmailOtpSender(Properties config) {
        this.fromEmail = config.getProperty("email.from");
        final String username = config.getProperty("email.username");
        final String password = config.getProperty("email.password");
        this.session = Session.getInstance(config, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    @Override
    public void send(String toEmail, String code) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        message.setSubject("Your OTP Code");
        message.setText("Your verification code is: " + code);
        Transport.send(message);
    }
}
