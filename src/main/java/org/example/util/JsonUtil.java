package org.example.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
                @Override
                public void write(JsonWriter out, LocalDateTime value) throws IOException {
                    out.value(value.toString());
                }

                @Override
                public LocalDateTime read(JsonReader in) throws IOException {
                    String value = in.nextString();
                    try {
                        return LocalDateTime.parse(value);
                    } catch (DateTimeParseException e) {
                        throw new JsonParseException("Date and time must use ISO-8601 format", e);
                    }
                }
            }.nullSafe())
            .create();

    public static <T> T fromJson(Reader reader, Class<T> type) {
        return GSON.fromJson(reader, type);
    }

    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static void sendJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(GSON.toJson(body));
    }

    public static void sendError(HttpServletResponse response, int status, String message) throws IOException {
        sendJson(response, status, Map.of("error", message));
    }
}
