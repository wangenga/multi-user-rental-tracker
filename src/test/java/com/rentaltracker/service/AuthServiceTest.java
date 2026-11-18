package com.rentaltracker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.rentaltracker.domain.User;
import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.CapturingLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.repository.exception.ConstraintViolationException;
import com.rentaltracker.security.BcryptPasswordHasher;
import com.rentaltracker.security.PasswordHasher;
import com.rentaltracker.service.exception.AuthenticationException;
import com.rentaltracker.service.exception.ValidationException;

class AuthServiceTest {

    static final String PASSWORD = "correct-horse-battery";

    @TempDir Path tempDir;
    Database db;
    PasswordHasher hasher = new BcryptPasswordHasher(BcryptPasswordHasher.MIN_COST);
    CapturingLogger logger = new CapturingLogger();
    AuthService auth;

    @BeforeEach
    void setUp() {
        db = new Database(tempDir.resolve("test.db").toString());
        auth = new AuthService(new UserRepository(db), hasher, logger);
    }

    @AfterEach
    void tearDown() { db.close(); }

    private String query(String sql) throws SQLException {
        try (Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private String userCount() throws SQLException { return query("SELECT count(*) FROM users"); }

    // ---------- register ----------

    @Test
    void registeringStoresAHashAndNeverThePlaintext() throws SQLException {
        User user = auth.register("liisa", PASSWORD);

        String stored = query("SELECT password FROM users WHERE id = " + user.getId());
        assertNotEquals(PASSWORD, stored);
        assertFalse(stored.contains(PASSWORD));
        assertTrue(stored.startsWith("$2"), "bcrypt hash expected but was " + stored);
        assertTrue(hasher.matches(PASSWORD, stored));
    }

    @Test
    void theSamePasswordGivesDifferentHashesForDifferentUsers() throws SQLException {
        auth.register("liisa", PASSWORD);
        auth.register("meelis", PASSWORD);

        assertNotEquals(query("SELECT password FROM users WHERE username = 'liisa'"),
                        query("SELECT password FROM users WHERE username = 'meelis'"));
    }

    @Test
    void theUsernameIsTrimmedAndLowercased() throws SQLException {
        auth.register("  Liisa ", PASSWORD);

        assertEquals("liisa", query("SELECT username FROM users"));
    }

    @Test
    void aTakenUsernameIsRefusedWithAReadableMessageEvenInAnotherCase() throws SQLException {
        auth.register("liisa", PASSWORD);

        ValidationException e = assertThrows(ValidationException.class,
                () -> auth.register("LIISA", "another-password"));

        assertEquals("That username is already taken", e.getMessage());
        assertEquals("username", e.getField());
        assertEquals("taken", e.getReason());
        assertEquals("1", userCount());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "bad name", "a!b"})
    void anInvalidUsernameIsRefusedAndNothingIsStored(String name) throws SQLException {
        assertThrows(ValidationException.class, () -> auth.register(name, PASSWORD));

        assertEquals("0", userCount());
    }

    @Test
    void usernameLengthLimits() throws SQLException {
        assertThrows(ValidationException.class, () -> auth.register("a".repeat(51), PASSWORD));
        assertEquals(50, auth.register("a".repeat(50), PASSWORD).getUsername().length());
        assertEquals(3, auth.register("abc", PASSWORD).getUsername().length());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        ", "short77"})
    void aMissingBlankOrShortPasswordIsRefusedAndNothingIsStored(String password) throws SQLException {
        assertThrows(ValidationException.class, () -> auth.register("liisa", password));

        assertEquals("0", userCount());
    }

    @Test
    void passwordLengthLimitsCountBytesBecauseBcryptReadsOnlyTheFirst72() throws SQLException {
        ValidationException tooLong = assertThrows(ValidationException.class,
                () -> auth.register("liisa", "a".repeat(73)));
        assertEquals("too_long", tooLong.getReason());

        // 37 two-byte characters is only 37 characters but 74 bytes
        assertThrows(ValidationException.class, () -> auth.register("liisa", "é".repeat(37)));

        auth.register("liisa", "a".repeat(72));
        auth.register("meelis", "12345678");
        assertEquals("2", userCount());
    }

    @Test
    void aPasswordIsStoredExactlyAsTypedIncludingSpaces() {
        auth.register("liisa", "  spaced pass  ");

        assertEquals("liisa", auth.login("liisa", "  spaced pass  ").username());
        assertThrows(AuthenticationException.class, () -> auth.login("liisa", "spaced pass"));
    }

    @Test
    void aDatabaseFailureOtherThanADuplicateIsNotDisguisedAsATakenUsername() {
        PasswordHasher nullHasher = new PasswordHasher() {
            @Override public String hash(String plain) { return null; }
            @Override public boolean matches(String plain, String stored) { return false; }
        };
        AuthService broken = new AuthService(new UserRepository(db), nullHasher, logger);

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class,
                () -> broken.register("liisa", PASSWORD));
        assertEquals(ConstraintViolationException.Type.NOT_NULL, e.getType());
    }

    // ---------- login ----------

    @Test
    void theRightPasswordLogsInAndReturnsTheSession() {
        User user = auth.register("liisa", PASSWORD);

        Session session = auth.login("liisa", PASSWORD);

        assertEquals(user.getId(), session.userId());
        assertEquals("liisa", session.username());
    }

    @Test
    void theUsernameIsMatchedIgnoringCaseAndSurroundingSpaces() {
        auth.register("liisa", PASSWORD);

        assertEquals("liisa", auth.login("  LIISA ", PASSWORD).username());
    }

    @Test
    void aWrongPasswordAndAnUnknownUsernameGiveTheSameMessage() {
        auth.register("liisa", PASSWORD);

        AuthenticationException wrong = assertThrows(AuthenticationException.class,
                () -> auth.login("liisa", "wrong-password"));
        AuthenticationException unknown = assertThrows(AuthenticationException.class,
                () -> auth.login("nobody", PASSWORD));

        assertEquals(AuthService.LOGIN_FAILED, wrong.getMessage());
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void passwordsAreCaseSensitive() {
        auth.register("liisa", PASSWORD);

        assertThrows(AuthenticationException.class, () -> auth.login("liisa", PASSWORD.toUpperCase()));
    }

    @Test
    void missingCredentialsFailTheLoginInsteadOfCrashing() {
        auth.register("liisa", PASSWORD);

        assertThrows(AuthenticationException.class, () -> auth.login(null, PASSWORD));
        assertThrows(AuthenticationException.class, () -> auth.login("liisa", null));
        assertThrows(AuthenticationException.class, () -> auth.login("", ""));
    }

    // ---------- logging ----------

    @Test
    void registeringAndLoggingInAreLoggedAtInfoWithOnlyIds() {
        User user = auth.register("liisa", PASSWORD);
        auth.login("liisa", PASSWORD);

        assertTrue(logger.has(AppLogger.Level.INFO, LogEvents.AUTH_REGISTERED));
        assertTrue(logger.has(AppLogger.Level.INFO, LogEvents.AUTH_LOGIN));
        assertEquals(String.valueOf(user.getId()), logger.entries().get(0).fields().get("user_id"));
    }

    @Test
    void failedLoginsAreLoggedAtWarnWithTheReason() {
        auth.register("liisa", PASSWORD);
        assertThrows(AuthenticationException.class, () -> auth.login("liisa", "wrong-password"));
        assertThrows(AuthenticationException.class, () -> auth.login("nobody", PASSWORD));

        var failures = logger.entries().stream()
                .filter(e -> e.event().equals(LogEvents.AUTH_LOGIN_FAILED)).toList();
        assertEquals(2, failures.size());
        assertEquals(AppLogger.Level.WARN, failures.get(0).level());
        assertEquals("bad_password", failures.get(0).fields().get("reason"));
        assertEquals("unknown_user", failures.get(1).fields().get("reason"));
    }

    @Test
    void noPasswordAndNoHashEverReachesTheLog() throws SQLException {
        auth.register("liisa", PASSWORD);
        String hash = query("SELECT password FROM users");
        assertThrows(AuthenticationException.class, () -> auth.login("liisa", "wrong-password"));
        assertThrows(AuthenticationException.class, () -> auth.login("nobody", PASSWORD));
        auth.login("liisa", PASSWORD);

        assertFalse(logger.entries().isEmpty());
        for (var entry : logger.entries()) {
            String text = entry.event() + " " + entry.fields();
            assertFalse(text.contains(PASSWORD), text);
            assertFalse(text.contains("wrong-password"), text);
            assertFalse(text.contains(hash), text);
        }
    }
}
