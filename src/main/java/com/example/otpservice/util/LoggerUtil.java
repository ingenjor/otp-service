package com.example.otpservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Вспомогательный класс для логирования (опционально).
 * В проекте используется прямое объявление логгера через LoggerFactory.
 */
public class LoggerUtil {
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
}
