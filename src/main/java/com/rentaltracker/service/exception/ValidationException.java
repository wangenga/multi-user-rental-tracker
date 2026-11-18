package com.rentaltracker.service.exception;

/**
 * Bad input. The message is safe to show the user, the other fields feed the log line:
 * entity=Item field=cost reason=negative
 *
 * Extends IllegalArgumentException so the existing menu catches keep working
 * until the central error handler replaces them.
 */
public class ValidationException extends IllegalArgumentException {

    private final String entity;
    private final String field;
    private final String reason;

    public ValidationException(String entity, String field, String reason, String userMessage) {
        super(userMessage);
        this.entity = entity;
        this.field = field;
        this.reason = reason;
    }

    public String getEntity() { return entity; }
    public String getField() { return field; }
    public String getReason() { return reason; }
}