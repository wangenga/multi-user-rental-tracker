package com.rentaltracker.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.rentaltracker.logging.AppLogger.Level;
import com.rentaltracker.logging.CapturingLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.repository.exception.ConstraintViolationException;
import com.rentaltracker.repository.exception.MappingException;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.repository.exception.RepositoryException;
import com.rentaltracker.service.exception.AuthenticationException;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.service.exception.NotOwnerException;
import com.rentaltracker.service.exception.ValidationException;

class ErrorHandlerTest {

    static final SQLException SQLITE_CAUSE =
            new SQLException("[SQLITE_CONSTRAINT_UNIQUE] A UNIQUE constraint failed (UNIQUE constraint failed: users.username)");

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final CapturingLogger logger = new CapturingLogger();
    final ErrorHandler handler = new ErrorHandler(
            new Prompter(new ByteArrayInputStream(new byte[0]), new PrintStream(out, true, UTF_8)), logger);

    private String shown() { return out.toString(UTF_8); }

    private CapturingLogger.Entry onlyEntry() {
        assertEquals(1, logger.entries().size(), logger.entries().toString());
        return logger.entries().get(0);
    }

    // ---------- messages written for the user are shown as they are ----------

    @Test
    void aBrokenBusinessRuleIsShownAsItIs() {
        handler.handle("inventory", new BusinessRuleException("Item is already rented: Drill"));

        assertTrue(shown().contains("Error: Item is already rented: Drill"));
    }

    @Test
    void anOwnershipRefusalIsShownAsItIs() {
        handler.handle("inventory", new NotOwnerException("You can only delist your own items."));

        assertTrue(shown().contains("Error: You can only delist your own items."));
    }

    @Test
    void aValidationFailureIsShownAsItIs() {
        handler.handle("browse_listings",
                new ValidationException("Rental", "days", "too_short", "Rental duration must be at least 1 day"));

        assertTrue(shown().contains("Error: Rental duration must be at least 1 day"));
    }

    @Test
    void otherBadInputIsShownAsItIs() {
        handler.handle("list_item", new IllegalArgumentException("Item name cannot be blank"));

        assertTrue(shown().contains("Error: Item name cannot be blank"));
    }

    @Test
    void aFailedLoginIsShownAsItIs() {
        handler.handle("startup", new AuthenticationException("Invalid username or password"));

        assertTrue(shown().contains("Error: Invalid username or password"));
    }

    // ---------- database failures: fixed message on screen, detail in the log ----------

    @Test
    void aMissingRecordGetsAPlainMessageAndAWarning() {
        handler.handle("confirm_return", new NotFoundException("Rental not found: id=42"));

        assertTrue(shown().contains(ErrorHandler.RECORD_GONE));
        assertFalse(shown().contains("id=42"));
        var entry = onlyEntry();
        assertEquals(Level.WARN, entry.level());
        assertEquals(LogEvents.RECORD_MISSING, entry.event());
        assertEquals("confirm_return", entry.fields().get("op"));
        assertEquals("Rental not found: id=42", entry.fields().get("detail"));
    }

    @Test
    void aConstraintViolationNeverShowsSqliteTextButLogsIt() {
        handler.handle("list_item", new ConstraintViolationException(
                ConstraintViolationException.Type.UNIQUE, "save user: unique", SQLITE_CAUSE));

        assertTrue(shown().contains(ErrorHandler.CONFLICT));
        assertFalse(shown().contains("SQLITE"));
        var entry = onlyEntry();
        assertEquals(Level.ERROR, entry.level());
        assertEquals(LogEvents.DB_ERROR, entry.event());
        assertEquals("constraint_violation", entry.fields().get("error"));
        assertEquals("UNIQUE", entry.fields().get("type"));
        assertTrue(entry.fields().get("detail").contains("SQLITE_CONSTRAINT_UNIQUE"));
    }

    @Test
    void aRowThatWontMapGetsAPlainMessageAndAnError() {
        handler.handle("inventory", new MappingException("Cannot map item row",
                new IllegalArgumentException("Unknown status: borrowed")));

        assertTrue(shown().contains(ErrorHandler.UNREADABLE));
        assertFalse(shown().contains("borrowed"));
        var entry = onlyEntry();
        assertEquals(Level.ERROR, entry.level());
        assertEquals("malformed_row", entry.fields().get("error"));
        assertEquals("Cannot map item row | cause: Unknown status: borrowed", entry.fields().get("detail"));
    }

    @Test
    void anyOtherDatabaseFailureGetsAPlainMessageAndAnError() {
        handler.handle("browse_listings", new RepositoryException("find listings failed: disk I/O error"));

        assertTrue(shown().contains(ErrorHandler.DATABASE_PROBLEM));
        assertFalse(shown().contains("disk I/O"));
        var entry = onlyEntry();
        assertEquals(Level.ERROR, entry.level());
        assertEquals("database", entry.fields().get("error"));
        assertEquals("find listings failed: disk I/O error", entry.fields().get("detail"));
    }

    @Test
    void aCauseWithoutAMessageLogsJustTheOuterMessage() {
        handler.handle("inventory", new RepositoryException("update item failed", new SQLException()));

        assertEquals("update item failed", onlyEntry().fields().get("detail"));
    }

    // ---------- bugs ----------

    @Test
    void anUnexpectedExceptionNeverShowsItsTypeOrMessage() {
        handler.handle("list_item", new IllegalStateException("No next page"));

        assertTrue(shown().contains(ErrorHandler.UNEXPECTED));
        assertFalse(shown().contains("IllegalStateException"));
        assertFalse(shown().contains("No next page"));
        var entry = onlyEntry();
        assertEquals(Level.ERROR, entry.level());
        assertEquals(LogEvents.UNEXPECTED, entry.event());
        assertEquals("java.lang.IllegalStateException", entry.fields().get("exception"));
        assertEquals("No next page", entry.fields().get("detail"));
    }

    @Test
    void anUnexpectedExceptionWithNoMessageIsStillLogged() {
        handler.handle("list_item", new NullPointerException());

        assertEquals("null", onlyEntry().fields().get("detail"));
    }

    @Test
    void messagesWrittenForTheUserAreNotLoggedHere() {
        // ownership refusals are logged by the service that detected them, validation by its own task
        handler.handle("inventory", new NotOwnerException("You can only delist your own items."));
        handler.handle("list_item", new IllegalArgumentException("Item name cannot be blank"));

        assertTrue(logger.entries().isEmpty());
    }
}
