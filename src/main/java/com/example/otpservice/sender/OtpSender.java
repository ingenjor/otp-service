package com.example.otpservice.sender;

public interface OtpSender {
    void send(String destination, String code) throws Exception;
}
