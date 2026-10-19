package ru.itmo.soa.flats.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the URL only: servlet getParameter() could also consume a form request body. */
public final class QueryParser {
    private final Map<String, List<String>> parameters = new LinkedHashMap<>();

    public QueryParser(HttpServletRequest request, Set<String> allowed, Set<String> repeated) {
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) return;
        try {
            for (String parameter : query.split("&", -1)) {
                String[] pair = parameter.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                if (!allowed.contains(key)) throw ApiException.badRequest("Неизвестный параметр: " + key);
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                List<String> values = parameters.computeIfAbsent(key, ignored -> new ArrayList<>());
                if (!values.isEmpty() && !repeated.contains(key)) throw ApiException.badRequest("Повтор параметра: " + key);
                values.add(value);
            }
        } catch (IllegalArgumentException e) { throw ApiException.badRequest("Некорректное URL-кодирование"); }
    }

    public String required(String key) {
        String value = optional(key);
        if (value == null) throw ApiException.badRequest("Отсутствует параметр: " + key);
        return value;
    }
    public String optional(String key) {
        List<String> values = parameters.get(key);
        return values == null ? null : values.get(0);
    }
    public List<String> all(String key) { return parameters.getOrDefault(key, List.of()); }
    public int positiveInt(String key, int fallback) {
        String value = optional(key);
        if (value == null) return fallback;
        long parsed = id(value);
        if (parsed > Integer.MAX_VALUE) throw ApiException.badRequest(key + ": число вне диапазона int32");
        return (int) parsed;
    }
    public static long id(String text) {
        try {
            if (!text.matches("[0-9]+")) throw new NumberFormatException();
            long value = Long.parseLong(text);
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw ApiException.badRequest("Ожидается целое число от 1 до " + Long.MAX_VALUE); }
    }
}
