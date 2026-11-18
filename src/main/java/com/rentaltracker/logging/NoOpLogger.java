package com.rentaltracker.logging;

/** Does nothing. Used until the real logger exists, and in tests that don't care about logs. */
public final class NoOpLogger implements AppLogger {
    @Override
    public void log(Level level, String event, Object... fields) {
        // intentionally empty
    }
}