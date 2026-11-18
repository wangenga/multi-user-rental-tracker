package com.rentaltracker.service.validation;

import java.nio.charset.StandardCharsets;

import com.rentaltracker.service.exception.ValidationException;

public final class UserValidator {

    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 50;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_BYTES = 72;

    private UserValidator() {}

    /** Trims, lowercases and validates. Returns the form that is stored. */
    public static String validateUsername(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("username", "empty", "Username cannot be empty");
        }
        String username = raw.trim().toLowerCase();
        if (username.length() < MIN_USERNAME_LENGTH) {
            throw invalid("username", "too_short",
                    "Username must be at least " + MIN_USERNAME_LENGTH + " characters");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw invalid("username", "too_long",
                    "Username must not exceed " + MAX_USERNAME_LENGTH + " characters");
        }
        if (!username.matches("[a-z0-9_]+")) {
            throw invalid("username", "bad_characters",
                    "Username may only contain lowercase letters, numbers, and underscores");
        }
        return username;
    }

    /** Passwords are never trimmed or changed. */
    public static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw invalid("password", "empty", "Password cannot be empty");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw invalid("password", "too_short",
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw invalid("password", "too_long",
                    "Password is too long (maximum " + MAX_PASSWORD_BYTES + " bytes)");
        }
    }

    private static ValidationException invalid(String field, String reason, String message) {
        return new ValidationException("User", field, reason, message);
    }
}
