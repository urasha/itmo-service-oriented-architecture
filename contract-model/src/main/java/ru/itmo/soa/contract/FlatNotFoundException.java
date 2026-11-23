package ru.itmo.soa.contract;

public final class FlatNotFoundException extends RuntimeException {
    public FlatNotFoundException(long id) {
        super("Квартира с id=" + id + " не найдена");
    }
}
