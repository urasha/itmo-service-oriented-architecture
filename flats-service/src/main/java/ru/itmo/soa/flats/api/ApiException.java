package ru.itmo.soa.flats.api;

public final class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static ApiException badRequest(String message) { return new ApiException(400, message); }
    public static ApiException notFound(long id) { return new ApiException(404, "Квартира с id=" + id + " не найдена"); }
}
