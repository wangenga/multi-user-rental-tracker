package com.rentaltracker.transport.screens;

import com.rentaltracker.service.AuthService;
import com.rentaltracker.service.Session;
import com.rentaltracker.service.exception.AuthenticationException;
import com.rentaltracker.service.exception.ValidationException;
import com.rentaltracker.transport.Prompter;

/**
 * The first screen: log in or register. It loops until one of them succeeds,
 * so the main menu only ever starts with a logged-in user.
 */
public class StartupScreen {

    private static final int LOG_IN = 1;
    private static final int REGISTER = 2;

    private final Prompter prompter;
    private final AuthService auth;

    public StartupScreen(Prompter prompter, AuthService auth) {
        this.prompter = prompter;
        this.auth = auth;
    }

    /** @return the session of the user who logged in or just registered */
    public Session run() {
        while (true) {
            showAccountQuestion();
            int choice = prompter.askChoice("> ", LOG_IN, REGISTER);
            try {
                Session session = choice == LOG_IN ? logIn() : register();
                prompter.println("Welcome, " + session.username() + ".");
                return session;
            } catch (AuthenticationException | ValidationException e) {
                // a wrong password, a taken username, a too-short password: show it and ask again
                prompter.println(e.getMessage());
            }
        }
    }

    private void showAccountQuestion() {
        prompter.println();
        prompter.println("=== Rental tracker ===");
        prompter.println("Do you have an account?");
        prompter.println("1) Log in");
        prompter.println("2) Register");
    }

    private Session logIn() {
        String username = prompter.ask("Username: ");
        String password = prompter.askPassword("Password: ");
        return auth.login(username, password);
    }

    /** A new account is logged in straight away, so the user goes on to the menu. */
    private Session register() {
        String username = prompter.ask("Choose a username: ");
        String password = prompter.askPassword("Choose a password: ");
        return Session.of(auth.register(username, password));
    }
}
