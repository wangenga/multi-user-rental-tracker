package com.rentaltracker.service.validation;

import com.rentaltracker.service.exception.ValidationException;

/** The one place that decides what a valid rental request looks like. */
public final class RentalValidator {

    public static final int MIN_DAYS = 1;
    // Team decision: the spec sets no upper limit, this just prevents absurd dates
    public static final int MAX_DAYS = 365;

    private RentalValidator() {}

    public static void validateDays(int days) {
        if (days < MIN_DAYS) {
            throw invalid("days", "too_short", "Rental duration must be at least 1 day");
        }
        if (days > MAX_DAYS) {
            throw invalid("days", "too_long", "Rental duration cannot be more than " + MAX_DAYS + " days");
        }
    }

    private static ValidationException invalid(String field, String reason, String message) {
        return new ValidationException("Rental", field, reason, message);
    }
}
