package com.rentaltracker.service.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.rentaltracker.service.exception.ValidationException;

class RentalValidatorTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 365})
    void acceptsDurationsFromOneDayToAYear(int days) {
        assertDoesNotThrow(() -> RentalValidator.validateDays(days));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsZeroAndNegativeDurationsWithAReadableMessage(int days) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> RentalValidator.validateDays(days));

        assertEquals("Rental duration must be at least 1 day", e.getMessage());
        assertEquals("Rental", e.getEntity());
        assertEquals("days", e.getField());
        assertEquals("too_short", e.getReason());
    }

    @ParameterizedTest
    @ValueSource(ints = {366, Integer.MAX_VALUE})
    void rejectsDurationsLongerThanAYear(int days) {
        ValidationException e = assertThrows(ValidationException.class,
                () -> RentalValidator.validateDays(days));

        assertEquals("too_long", e.getReason());
        assertTrue(e.getMessage().contains("365"), e.getMessage());
    }
}
