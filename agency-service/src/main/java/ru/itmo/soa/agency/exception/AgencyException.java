package ru.itmo.soa.agency.exception;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class AgencyException extends RuntimeException {
    private final int status;

    public AgencyException(int status, String message) {
        super(message);
        this.status = status;
    }

    public AgencyException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public static AgencyException upstream() {
        return new AgencyException(502, "Ошибка обращения к сервису квартир");
    }

    public static AgencyException upstream(Throwable cause) {
        return new AgencyException(502, "Ошибка обращения к сервису квартир", cause);
    }

    public static AgencyException timeout() {
        return new AgencyException(504, "Истекло время ожидания сервиса квартир");
    }
}
