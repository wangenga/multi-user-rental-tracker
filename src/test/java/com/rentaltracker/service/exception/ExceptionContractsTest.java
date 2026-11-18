package com.rentaltracker.service.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionContractsTest {

    @Test
    void validationExceptionCarriesTheLogFieldsAndTheUserMessage() {
        ValidationException e = new ValidationException(
                "Item", "cost", "negative", "Cost per day must be at least 1");

        assertEquals("Cost per day must be at least 1", e.getMessage());
        assertEquals("Item", e.getEntity());
        assertEquals("cost", e.getField());
        assertEquals("negative", e.getReason());
        assertInstanceOf(IllegalArgumentException.class, e);
    }

    @Test
    void authenticationExceptionKeepsItsMessage() {
        assertEquals("Invalid username or password", new AuthenticationException("Invalid username or password").getMessage());
    }
}
