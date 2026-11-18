package com.rentaltracker.logging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoggingContractsTest {

    @Test
    void noOpLoggerAcceptsEveryLevelWithoutFailing() {
        AppLogger logger = new NoOpLogger();

        assertDoesNotThrow(() -> {
            logger.info(LogEvents.RENTAL_CREATED, "rental_id", 1);
            logger.warn(LogEvents.VALIDATION_FAILED, "field", "cost");
            logger.error(LogEvents.DB_ERROR);
        });
    }

    @Test
    void capturingLoggerKeepsLevelEventAndFieldsInOrder() {
        CapturingLogger logger = new CapturingLogger();

        logger.warn(LogEvents.VALIDATION_FAILED, "entity", "Item", "field", "cost", "reason", "negative");

        assertEquals(1, logger.entries().size());
        var entry = logger.entries().get(0);
        assertEquals(AppLogger.Level.WARN, entry.level());
        assertEquals(LogEvents.VALIDATION_FAILED, entry.event());
        assertEquals("[entity, field, reason]", entry.fields().keySet().toString());
        assertTrue(logger.has(AppLogger.Level.WARN, LogEvents.VALIDATION_FAILED));
        assertFalse(logger.has(AppLogger.Level.ERROR, LogEvents.VALIDATION_FAILED));
    }
}
