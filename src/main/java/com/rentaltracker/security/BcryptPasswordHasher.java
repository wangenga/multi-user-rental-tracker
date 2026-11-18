package com.rentaltracker.security;

import com.password4j.BcryptFunction;
import com.password4j.Password;

public class BcryptPasswordHasher implements PasswordHasher {

    public static final int DEFAULT_COST = 12;   // production strength
    public static final int MIN_COST = 4;        // bcrypt's minimum, used by tests to stay fast

    private final BcryptFunction bcrypt;

    public BcryptPasswordHasher() {
        this(DEFAULT_COST);
    }

    public BcryptPasswordHasher(int cost) {
        if (cost < MIN_COST || cost > 31) {
            throw new IllegalArgumentException("bcrypt cost must be between 4 and 31");
        }
        this.bcrypt = BcryptFunction.getInstance(BcryptFunction.Bcrypt.B, cost);
    }

    @Override
    public String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return Password.hash(plainPassword).with(bcrypt).getResult();
    }

    @Override
    public boolean matches(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return Password.check(plainPassword, storedHash).with(bcrypt);
    }
}
