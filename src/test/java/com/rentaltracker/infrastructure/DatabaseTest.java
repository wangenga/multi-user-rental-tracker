package com.rentaltracker.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.rentaltracker.repository.exception.DatabaseConnectionException;
import com.rentaltracker.repository.exception.RepositoryException;

class DatabaseTest {

    @TempDir Path tempDir;

    // ---------- open / connection ----------

    @Test
    void constructorOpensConnection() throws SQLException {
        try (Database db = new Database(tempDir.resolve("ok.db").toString())) {
            Connection c = db.connection();
            assertNotNull(c);
            assertFalse(c.isClosed());
        }
    }

    @Test
    void constructorCreatesTheSchema() throws SQLException {
        try (Database db = new Database(tempDir.resolve("schema.db").toString());
             Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT count(*) FROM sqlite_master WHERE type = 'table' "
                             + "AND name IN ('users', 'listed_items', 'rentals')")) {
            rs.next();
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    void foreignKeysAreEnabled() throws SQLException {
        try (Database db = new Database(tempDir.resolve("fk.db").toString());
             Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void openingAnExistingDatabaseIsAllowed() {
        // Re-opening the same path runs the schema again; must be idempotent.
        String path = tempDir.resolve("reopen.db").toString();
        assertDoesNotThrow(() -> {
            try (Database first = new Database(path)) {
                // first user triggers init
            }
            try (Database second = new Database(path)) {
                assertNotNull(second.connection());
            }
        });
    }

    // ---------- constructor failure paths ----------

    @Test
    void badPathThrowsDatabaseConnectionException() {
        Path bad = tempDir.resolve("no-such-dir/nested/x.db");
        assertThrows(DatabaseConnectionException.class, () -> new Database(bad.toString()));
    }

    @Test
    void badPathMessageMentionsThePath() {
        Path bad = tempDir.resolve("no-such-dir/x.db");
        DatabaseConnectionException e = assertThrows(DatabaseConnectionException.class,
                () -> new Database(bad.toString()));
        assertTrue(e.getMessage().contains(bad.toString()),
                "message should name the path, got: " + e.getMessage());
    }

    @Test
    void directoryWhereFileShouldBeThrowsDatabaseConnectionException() throws Exception {
        // SQLite cannot open a *directory* as a database file.
        Path dirAsFile = tempDir.resolve("as-file.db");
        Files.createDirectories(dirAsFile);
        assertThrows(DatabaseConnectionException.class, () -> new Database(dirAsFile.toString()));
    }

    // ---------- close ----------

    @Test
    void closeDoesNotThrowOnHealthyDatabase() {
        Database db = new Database(tempDir.resolve("close.db").toString());
        assertDoesNotThrow(db::close);
    }

    @Test
    void closeAfterCloseIsSafe() {
        // SQLite's Connection.close() is a no-op when already closed; the wrapper
        // should not turn that into a RepositoryException.
        Database db = new Database(tempDir.resolve("double-close.db").toString());
        db.close();
        assertDoesNotThrow(db::close);
    }

    @Test
    void connectionIsUnusableAfterClose() throws SQLException {
        Database db = new Database(tempDir.resolve("unusable.db").toString());
        Connection c = db.connection();
        db.close();
        assertTrue(c.isClosed());
    }

    @Test
    void constructorFailureIsDatabaseConnectionExceptionNotRawRuntimeException() {
        Path bad = tempDir.resolve("missing/x.db");
        Throwable t = assertThrows(Throwable.class, () -> new Database(bad.toString()));
        assertInstanceOf(DatabaseConnectionException.class, t);
    }
}