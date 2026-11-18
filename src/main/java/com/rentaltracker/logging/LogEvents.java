package com.rentaltracker.logging;

/** Every event name in one place, so layers and tests cannot drift apart. */
public final class LogEvents {
    private LogEvents() {}

    // key rental events
    public static final String ITEM_LISTED = "item.listed";
    public static final String RENTAL_CREATED = "rental.created";
    public static final String RENTAL_RETURNED = "rental.returned";

    // accounts
    public static final String AUTH_REGISTERED = "auth.registered";
    public static final String AUTH_LOGIN = "auth.login";
    public static final String AUTH_LOGIN_FAILED = "auth.login_failed";
    public static final String AUTH_LOGOUT = "auth.logout";

    // failures
    public static final String VALIDATION_FAILED = "validation.failed";
    public static final String RULE_VIOLATION = "rule.violated";
    public static final String DB_ERROR = "db.query";
    public static final String UNEXPECTED = "app.unexpected";

    // resource lifecycle
    public static final String DB_OPENED = "db.opened";
    public static final String DB_CLOSED = "db.closed";
}