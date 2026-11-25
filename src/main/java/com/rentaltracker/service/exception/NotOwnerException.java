package com.rentaltracker.service.exception;

/**
 * The logged-in user tried to act on an item they don't own.
 * A business rule like any other, so every existing catch shows the message and
 * the user goes back to the menu. The message never names the real owner.
 */
public class NotOwnerException extends BusinessRuleException {
    public NotOwnerException(String message) {
        super(message);
    }
}
