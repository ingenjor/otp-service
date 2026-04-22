package com.example.otpservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JsonUtil {
    public static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T parse(InputStream is, Class<T> clazz) throws IOException {
        return mapper.readValue(is, clazz);
    }

    public static void writeJson(OutputStream os, Object obj) throws IOException {
        mapper.writeValue(os, obj);
    }

    public static String toJson(Object obj) throws IOException {
        return mapper.writeValueAsString(obj);
    }

    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    public static JsonNode readTree(InputStream is) throws IOException {
        return mapper.readTree(is);
    }
}
