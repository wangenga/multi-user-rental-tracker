package com.rentaltracker.service.validation;

import com.rentaltracker.service.exception.ValidationException;

/**
 * The one place that decides what a valid item listing looks like.
 *
 * The service calls these methods before anything reaches the repository, so a bad
 * input never touches the database. Each failure throws a ValidationException that
 * carries two things:
 *   - a short message that is safe to show the user ("Cost per day cannot be negative")
 *   - entity / field / reason for the log line (entity=Item field=cost reason=negative)
 *
 * Methods that take text return the cleaned-up value (trimmed), so the service stores
 * exactly what was validated and never has to repeat the clean-up itself.
 *
 * Bad references (an item id that does not exist, an item you do not own) are NOT checked
 * here: they need a database lookup, so the service handles them.
 */
public final class ItemValidator {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    // Only static methods, so nobody should create an instance
    private ItemValidator() {}

    /**
     * The name is required. Blank or whitespace-only is rejected.
     *
     * @return the trimmed name, ready to store
     */
    public static String validateName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("name", "empty", "Item name cannot be empty");
        }
        String name = raw.trim();
        // Length is checked after trimming, so surrounding spaces do not count
        if (name.length() > MAX_NAME_LENGTH) {
            throw invalid("name", "too_long",
                    "Item name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    /**
     * The description is optional. A missing one (null) is stored as empty text,
     * but a very long one is rejected so a single listing cannot flood the screen.
     *
     * @return the trimmed description, or "" when none was given
     */
    public static String validateDescription(String raw) {
        String description = raw == null ? "" : raw.trim();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("description", "too_long",
                    "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return description;
    }

    // Every failure in this class is about an Item, so the entity name is filled in once here
    private static ValidationException invalid(String field, String reason, String message) {
        return new ValidationException("Item", field, reason, message);
    }
}
