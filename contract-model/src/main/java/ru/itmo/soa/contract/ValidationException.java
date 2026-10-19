package ru.itmo.soa.contract;

/** Framework-independent distinction between malformed input and model constraints. */
public final class ValidationException extends RuntimeException {
    private final boolean constraint;

    public ValidationException(String message, boolean constraint) {
        super(message);
        this.constraint = constraint;
    }

    public boolean isConstraint() { return constraint; }
    public static ValidationException structure(String message) { return new ValidationException(message, false); }
    public static ValidationException constraint(String message) { return new ValidationException(message, true); }
}
