package ru.itmo.soa.flats.exception;

public final class FlatNotFoundException extends RuntimeException {
    public FlatNotFoundException(long id) {
        super("Квартира с id=" + id + " не найдена");
    }
}
