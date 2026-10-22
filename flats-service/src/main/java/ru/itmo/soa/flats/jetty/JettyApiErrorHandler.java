package ru.itmo.soa.flats.jetty;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class JettyApiErrorHandler extends ErrorHandler {
    @Override
    public boolean errorPageForMethod(String method) {

        return true;
    }

    @Override
    protected void generateResponse(
            Request request,
            Response response,
            int code,
            String ignoredMessage,
            Throwable cause,
            Callback callback) {

        String message =
                switch (code) {
                    case 414 -> "Слишком длинный URL";
                    case 431 -> "Слишком большие HTTP-заголовки";
                    case 404 -> "Ресурс не найден";
                    default -> "Ошибка обработки HTTP-запроса";
                };
        byte[] json =
                ("{\"timestamp\":\"" + Instant.now() + "\",\"message\":\"" + message + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store");
        response.write(true, ByteBuffer.wrap(json), callback);
    }
}
