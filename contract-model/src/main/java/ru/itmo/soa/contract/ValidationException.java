package ru.itmo.soa.contract;

import lombok.Getter;

@Getter
public final class ValidationException extends RuntimeException {
    private final boolean constraint;

    public ValidationException(String message, boolean constraint) {
        super(message);
        this.constraint = constraint;
    }

    public static ValidationException structure(String message) {
        return new ValidationException(message, false);
    }

    public static ValidationException constraint(String message) {
        return new ValidationException(message, true);
    }
}
