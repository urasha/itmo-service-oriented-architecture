package ru.itmo.soa.flats.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ru.itmo.soa.contract.ApiError;
import ru.itmo.soa.contract.ValidationException;

import java.time.Clock;
import java.time.Instant;
import java.util.logging.Level;

@RestControllerAdvice
@Log
@RequiredArgsConstructor
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final Clock clock;

    @ExceptionHandler(FlatNotFoundException.class)
    public ResponseEntity<Object> notFound(FlatNotFoundException e) {
        return error(404, e.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> api(ApiException e) {
        return error(e.status(), e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> validation(ValidationException e) {
        return error(e.isConstraint() ? 422 : 400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception e) {
        log.log(Level.SEVERE, "Request failed", e);
        return error(500, "Внутренняя ошибка сервера");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(
                new ApiError(Instant.now(clock), httpMessage(status.value())),
                responseHeaders,
                status);
    }

    private String httpMessage(int status) {
        return switch (status) {
            case 404 -> "Ресурс не найден";
            case 405 -> "HTTP-метод не поддерживается для этого URI";
            case 406 -> "Поддерживается формат application/json";
            default -> "Некорректный HTTP-запрос";
        };
    }

    private ResponseEntity<Object> error(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(Instant.now(clock), message));
    }
}
