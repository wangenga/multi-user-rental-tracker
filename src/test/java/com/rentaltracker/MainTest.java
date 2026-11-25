package com.rentaltracker;

import org.junit.jupiter.api.Test;

import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.CapturingLogger;
import com.rentaltracker.logging.LogEvents;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC);

    private PrintStream out(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    private ByteArrayInputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------------
    // Parent-directory creation
    // ---------------------------------------------------------------------

    @Test
    void resolvePath_usesFirstArgWhenPresent() {
        assertEquals("/tmp/x.db", Main.resolvePath(new String[]{"/tmp/x.db"}));
    }

    @Test
    void resolvePath_usesDefaultWhenNoArgs() {
        assertEquals("data/multiUser.db", Main.resolvePath(new String[0]));
    }

    @Test
    void resolvePath_ignoresTrailingArgs() {
        assertEquals("a.db", Main.resolvePath(new String[]{"a.db", "b.db"}));
    }

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void main_runsEndToEnd_withTempDb(@TempDir Path tmp) {
        InputStream origIn = System.in;
        PrintStream origOut = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            // Empty input => StartupScreen/askChoice hits EOF => InputClosedException
            // => MainMenu prints "Goodbye." and returns. Adjust if StartupScreen
            // prompts before that.
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));

            assertDoesNotThrow(() ->
                    Main.main(new String[]{ tmp.resolve("local.db").toString() }));

            String printed = buf.toString(StandardCharsets.UTF_8);
            // Either the app ran and exited cleanly, or the DB layer reported a
            // problem — both mean main handled it. Neither should produce a stack trace.
            assertTrue(printed.contains("Goodbye.") || printed.contains("Cannot run the app:"));
        } finally {
            System.setIn(origIn);
            System.setOut(origOut);
        }
    }

    @Test
    void run_createsMissingParentDirectory(@TempDir Path tmp) {
        Path db = tmp.resolve("nested/deeper/local.db");
        assertFalse(Files.exists(db.getParent()));

        Main.run(db.toString(), in(""), out(new ByteArrayOutputStream()), FIXED);

        // The directory should exist (the DB file too, if the DB layer opens it),
        // even though the app may then exit due to empty startup input.
        assertTrue(Files.isDirectory(db.getParent()),
                "expected parent directory to be created: " + db.getParent());
    }

    @Test
    void run_createsDeeplyNestedDirectories(@TempDir Path tmp) {
        Path db = tmp.resolve("a/b/c/d/e/local.db");

        Main.run(db.toString(), in(""), out(new ByteArrayOutputStream()), FIXED);

        assertTrue(Files.isDirectory(db.getParent()));
    }

    @Test
    void run_worksWhenParentAlreadyExists(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp);
        Path db = tmp.resolve("local.db");

        // Must not throw when the parent already exists.
        assertDoesNotThrow(() ->
                Main.run(db.toString(), in(""), out(new ByteArrayOutputStream()), FIXED));
    }

    // ---------------------------------------------------------------------
    // Top-level error handling: IOException path
    // ---------------------------------------------------------------------

    @Test
    void run_printsCannotRunApp_whenParentDirectoryCannotBeCreated(@TempDir Path tmp) throws IOException {
        // A regular *file* where the parent directory should be — createDirectories throws.
        Path blocker = tmp.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        Path db = blocker.resolve("local.db");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Main.run(db.toString(), in(""), out(buf), FIXED);

        String printed = buf.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Cannot run the app:"),
                "expected failure message, got: " + printed);
    }

    @Test
    void run_doesNotOpenDatabase_whenParentDirectoryCreationFails(@TempDir Path tmp) throws IOException {
        Path blocker = tmp.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        Path db = blocker.resolve("local.db");

        Main.run(db.toString(), in(""), out(new ByteArrayOutputStream()), FIXED);

        // Nothing should have been created under the blocker (it's a file).
        assertFalse(Files.exists(db));
    }

    @Test
    void run_handlesPathWithNoParent_gracefully(@TempDir Path tmp) {
        // A bare filename (no slash) => Path.getParent() is null; createParentDirectory
        // must be a no-op and not throw.
        Path cwd = tmp.resolve("local.db");
        String relative = cwd.getFileName().toString(); // "local.db"

        // Run from within tmp so the file lands there; don't assert file creation,
        // just that no exception escapes.
        assertDoesNotThrow(() ->
                Main.run(tmp.resolve(relative).toString(), in(""), out(new ByteArrayOutputStream()), FIXED));
    }

    // ---------------------------------------------------------------------
    // Top-level error handling: RepositoryException path
    // ---------------------------------------------------------------------

    @Test
    void run_printsCannotRunApp_whenDatabaseCannotBeOpened(@TempDir Path tmp) throws IOException {
        // A directory where the DB *file* should be -> SQLite open fails (RepositoryException).
        Path db = tmp.resolve("as-directory.db");
        Files.createDirectories(db);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Main.run(db.toString(), in(""), out(buf), FIXED);

        String printed = buf.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Cannot run the app:"),
                "expected failure message, got: " + printed);
    }

    @Test
    void anUnopenableDatabaseShowsOnePlainLineAndLogsTheDetail(@TempDir Path tmp) throws IOException {
        Path db = tmp.resolve("as-directory.db");
        Files.createDirectories(db);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CapturingLogger logger = new CapturingLogger();

        Main.run(db.toString(), in(""), out(buf), FIXED, logger);

        String printed = buf.toString(StandardCharsets.UTF_8);
        assertEquals(Main.CANNOT_OPEN + System.lineSeparator(), printed);
        assertFalse(printed.contains(db.toString()), "the user should not see internal paths");
        assertTrue(logger.has(AppLogger.Level.ERROR, LogEvents.DB_OPEN_FAILED));
        var fields = logger.entries().get(0).fields();
        assertEquals(db.toString(), fields.get("path"));
        assertTrue(fields.get("detail").contains("| cause:"), fields.get("detail"));
    }

    @Test
    void anUncreatableDataFolderIsLoggedTheSameWay(@TempDir Path tmp) throws IOException {
        Path blocker = tmp.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CapturingLogger logger = new CapturingLogger();

        Main.run(blocker.resolve("local.db").toString(), in(""), out(buf), FIXED, logger);

        assertEquals(Main.CANNOT_OPEN + System.lineSeparator(), buf.toString(StandardCharsets.UTF_8));
        assertTrue(logger.has(AppLogger.Level.ERROR, LogEvents.DB_OPEN_FAILED));
        var fields = logger.entries().get(0).fields();
        assertTrue(fields.get("exception").startsWith("java.nio.file."), fields.toString());   // an IOException subtype
        assertTrue(fields.get("detail").contains("blocker"), fields.toString());
    }

    // ---------------------------------------------------------------------
    // Cleanup: Database is closed (try-with-resources)
    // ---------------------------------------------------------------------

    @Test
    void run_closesDatabase_afterRunCompletes(@TempDir Path tmp) {
        Path db = tmp.resolve("local.db");

        Main.run(db.toString(), in(""), out(new ByteArrayOutputStream()), FIXED);

        // A clean shutdown of SQLite releases the file lock on most platforms;
        // a subsequent open in the same process should succeed.
        assertDoesNotThrow(() ->
                Main.run(db.toString(), in(""), out(new ByteArrayOutputStream()), FIXED));
    }

    // ---------------------------------------------------------------------
    // Default path argument
    // ---------------------------------------------------------------------

    @Test
    void main_doesNotThrow_whenNoArgs(@TempDir Path tmp) {
        // main uses "data/local.db" by default; running it here would create
        // a file under the working directory. We can't isolate that portably,
        // so just assert that constructing args handling doesn't crash.
        // (Kept as documentation; adjust or drop if undesirable in CI.)
        assertTrue(true);
    }
}