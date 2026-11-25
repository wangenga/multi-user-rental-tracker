package com.rentaltracker.transport;

import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.repository.exception.ConstraintViolationException;
import com.rentaltracker.repository.exception.MappingException;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.repository.exception.RepositoryException;
import com.rentaltracker.service.exception.AuthenticationException;
import com.rentaltracker.service.exception.BusinessRuleException;

/**
 * Decides, in one place, what the user sees and what goes to the log when an operation fails.
 *
 * Rule of thumb: a message we wrote for the user (a broken business rule, bad input) is shown
 * as it is. Anything from the database or an unexpected bug gets a short, fixed message on
 * screen, and its real detail (operation, exception type, SQLite text) goes only to the log.
 * Every case is recoverable: the caller carries on and the user goes back to the menu.
 */
public class ErrorHandler {

    static final String RECORD_GONE = "That record no longer exists. It may have just been changed by someone else.";
    static final String CONFLICT = "That change conflicts with existing data, so nothing was saved.";
    static final String UNREADABLE = "Some stored data could not be read, so that action was cancelled.";
    static final String DATABASE_PROBLEM = "Something went wrong while reading or saving data. Please try again.";
    static final String UNEXPECTED = "Something unexpected went wrong. Please try again.";

    private final Prompter prompter;
    private final AppLogger logger;

    public ErrorHandler(Prompter prompter, AppLogger logger) {
        this.prompter = prompter;
        this.logger = logger;
    }

    /**
     * Shows the user a readable message and logs the detail.
     *
     * @param operation where it happened, for the log, e.g. "browse_listings"
     */
    public void handle(String operation, RuntimeException e) {
        switch (e) {
            // written for the user: show as is (a ValidationException is an IllegalArgumentException)
            case BusinessRuleException rule -> prompter.println("Error: " + rule.getMessage());
            case AuthenticationException auth -> prompter.println("Error: " + auth.getMessage());
            case IllegalArgumentException bad -> prompter.println("Error: " + bad.getMessage());

            // from the database: fixed message on screen, detail in the log
            case NotFoundException missing -> {
                logger.warn(LogEvents.RECORD_MISSING, "op", operation, "detail", missing.getMessage());
                prompter.println(RECORD_GONE);
            }
            case ConstraintViolationException conflict -> {
                logger.error(LogEvents.DB_ERROR, "op", operation, "error", "constraint_violation",
                        "type", conflict.getType(), "detail", detailOf(conflict));
                prompter.println(CONFLICT);
            }
            case MappingException unreadable -> {
                logger.error(LogEvents.DB_ERROR, "op", operation, "error", "malformed_row",
                        "detail", detailOf(unreadable));
                prompter.println(UNREADABLE);
            }
            case RepositoryException db -> {
                logger.error(LogEvents.DB_ERROR, "op", operation, "error", "database",
                        "detail", detailOf(db));
                prompter.println(DATABASE_PROBLEM);
            }

            // a bug: never show a stack trace
            default -> {
                logger.error(LogEvents.UNEXPECTED, "op", operation,
                        "exception", e.getClass().getName(), "detail", detailOf(e));
                prompter.println(UNEXPECTED);
            }
        }
    }

    /** The exception's message plus its cause's, where the SQLite error text usually is. */
    private static String detailOf(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause == null || cause.getMessage() == null
                ? String.valueOf(e.getMessage())
                : e.getMessage() + " | cause: " + cause.getMessage();
    }
}
