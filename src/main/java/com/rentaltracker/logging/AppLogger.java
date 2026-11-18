package com.rentaltracker.logging;

public interface AppLogger {

    enum Level { INFO, WARN, ERROR }

    /** fields are alternating key, value pairs: log(Level.INFO, "rental.created", "rental_id", 42) */
    void log(Level level, String event, Object... fields);

    default void info(String event, Object... fields)  { log(Level.INFO, event, fields); }
    default void warn(String event, Object... fields)  { log(Level.WARN, event, fields); }
    default void error(String event, Object... fields) { log(Level.ERROR, event, fields); }
}
