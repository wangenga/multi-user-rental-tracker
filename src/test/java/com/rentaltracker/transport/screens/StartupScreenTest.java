package com.rentaltracker.transport.screens;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rentaltracker.domain.User;
import com.rentaltracker.service.AuthService;
import com.rentaltracker.service.Session;
import com.rentaltracker.service.exception.AuthenticationException;
import com.rentaltracker.service.exception.ValidationException;
import com.rentaltracker.transport.Prompter;
import com.rentaltracker.transport.exception.InputClosedException;

class StartupScreenTest {

    AuthService auth;
    ByteArrayOutputStream out;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        out = new ByteArrayOutputStream();
    }

    private StartupScreen screen(String input) {
        Prompter prompter = new Prompter(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        return new StartupScreen(prompter, auth);
    }

    private String printed() {
        return out.toString(StandardCharsets.UTF_8);
    }

    // ---------- the account question ----------

    @Test
    void asksWhetherTheUserHasAnAccount() {
        when(auth.login("liisa", "secret-pass")).thenReturn(new Session(1, "liisa"));

        screen("1\nliisa\nsecret-pass\n").run();

        assertTrue(printed().contains("=== Rental tracker ==="));
        assertTrue(printed().contains("Do you have an account?"));
        assertTrue(printed().contains("1) Log in"));
        assertTrue(printed().contains("2) Register"));
    }

    @Test
    void anOutOfRangeChoiceIsAskedAgain() {
        when(auth.login("liisa", "secret-pass")).thenReturn(new Session(1, "liisa"));

        Session session = screen("3\n1\nliisa\nsecret-pass\n").run();

        assertEquals(1, session.userId());
        assertTrue(printed().contains("Please enter a number from 1 to 2."));
    }

    // ---------- log in ----------

    @Test
    void loggingInReturnsTheSessionFromTheAuthService() {
        Session expected = new Session(4, "liisa");
        when(auth.login("liisa", "secret-pass")).thenReturn(expected);

        Session session = screen("1\nliisa\nsecret-pass\n").run();

        assertSame(expected, session);
        assertTrue(printed().contains("Welcome, liisa."));
        verify(auth, never()).register(anyString(), anyString());
    }

    @Test
    void aFailedLoginShowsTheMessageAndAsksAgain() {
        when(auth.login("liisa", "wrong-pass"))
                .thenThrow(new AuthenticationException(AuthService.LOGIN_FAILED));
        when(auth.login("liisa", "secret-pass")).thenReturn(new Session(1, "liisa"));

        Session session = screen("1\nliisa\nwrong-pass\n1\nliisa\nsecret-pass\n").run();

        assertEquals("liisa", session.username());
        assertTrue(printed().contains(AuthService.LOGIN_FAILED));
        verify(auth, times(2)).login(eq("liisa"), anyString());
    }

    @Test
    void thePasswordIsPassedExactlyAsTypedButTheUsernameIsTrimmed() {
        when(auth.login("liisa", "  spaced pass  ")).thenReturn(new Session(1, "liisa"));

        screen("1\n  liisa  \n  spaced pass  \n").run();

        verify(auth).login("liisa", "  spaced pass  ");
    }

    // ---------- register ----------

    @Test
    void registeringLogsTheNewUserInStraightAway() {
        when(auth.register("meelis", "secret-pass"))
                .thenReturn(new User(5, "meelis", "$2b$04$hash", "2026-06-16 14:30"));

        Session session = screen("2\nmeelis\nsecret-pass\n").run();

        assertEquals(new Session(5, "meelis"), session);
        assertTrue(printed().contains("Welcome, meelis."));
        verify(auth, never()).login(anyString(), anyString());
    }

    @Test
    void aRejectedRegistrationShowsTheMessageAndAsksAgain() {
        when(auth.register("liisa", "secret-pass")).thenThrow(
                new ValidationException("User", "username", "taken", "That username is already taken"));
        when(auth.register("liisa2", "secret-pass"))
                .thenReturn(new User(6, "liisa2", "$2b$04$hash", "2026-06-16 14:30"));

        Session session = screen("2\nliisa\nsecret-pass\n2\nliisa2\nsecret-pass\n").run();

        assertEquals(6, session.userId());
        assertTrue(printed().contains("That username is already taken"));
    }

    @Test
    void afterAFailedRegistrationTheUserCanChooseToLogInInstead() {
        when(auth.register("liisa", "secret-pass")).thenThrow(
                new ValidationException("User", "username", "taken", "That username is already taken"));
        when(auth.login("liisa", "secret-pass")).thenReturn(new Session(1, "liisa"));

        Session session = screen("2\nliisa\nsecret-pass\n1\nliisa\nsecret-pass\n").run();

        assertEquals(1, session.userId());
    }

    // ---------- end of input ----------

    @Test
    void endOfInputIsPassedUpSoTheMenuCanExit() {
        assertThrows(InputClosedException.class, () -> screen("").run());
        assertThrows(InputClosedException.class, () -> screen("1\nliisa\n").run());
    }
}
