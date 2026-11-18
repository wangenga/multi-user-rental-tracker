package com.rentaltracker.service;

import java.util.Optional;

import com.rentaltracker.domain.User;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.repository.exception.ConstraintViolationException;
import com.rentaltracker.security.PasswordHasher;
import com.rentaltracker.service.exception.AuthenticationException;
import com.rentaltracker.service.exception.ValidationException;
import com.rentaltracker.service.validation.UserValidator;

public class AuthService {

    /** One message for an unknown user and a wrong password, so usernames are not revealed. */
    public static final String LOGIN_FAILED = "Invalid username or password";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AppLogger logger;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordHasher hasher, AppLogger logger) {
        this.users = users;
        this.hasher = hasher;
        this.logger = logger;
        this.dummyHash = hasher.hash("timing-equaliser-not-a-real-password");
    }

    public User register(String username, String password) {
        String name = UserValidator.validateUsername(username);
        UserValidator.validatePassword(password);

        String hash = hasher.hash(password);
        User saved;
        try {
            saved = users.save(new User(0, name, hash, null));
        } catch (ConstraintViolationException e) {
            if (e.getType() == ConstraintViolationException.Type.UNIQUE) {
                throw new ValidationException("User", "username", "taken",
                        "That username is already taken");
            }
            throw e;
        }
        logger.info(LogEvents.AUTH_REGISTERED, "user_id", saved.getId());
        return saved;
    }

    public Session login(String username, String password) {
        String name = username == null ? "" : username.trim().toLowerCase();
        String attempt = password == null ? "" : password;

        Optional<User> found = users.findByUsername(name);
        if (found.isEmpty()) {
            hasher.matches(attempt, dummyHash);   // same work as a real check
            logger.warn(LogEvents.AUTH_LOGIN_FAILED, "reason", "unknown_user");
            throw new AuthenticationException(LOGIN_FAILED);
        }

        User user = found.get();
        if (!hasher.matches(attempt, user.getPassword())) {
            logger.warn(LogEvents.AUTH_LOGIN_FAILED, "reason", "bad_password", "user_id", user.getId());
            throw new AuthenticationException(LOGIN_FAILED);
        }

        logger.info(LogEvents.AUTH_LOGIN, "user_id", user.getId());
        return Session.of(user);
    }
}
