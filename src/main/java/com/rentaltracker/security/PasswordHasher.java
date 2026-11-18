package com.rentaltracker.security;

public interface PasswordHasher {
    /** Returns a salted hash, safe to store. */
    String hash(String plainPassword);

    /** True only if the password matches the stored hash. Never throws for a bad hash. */
    boolean matches(String plainPassword, String storedHash);
}