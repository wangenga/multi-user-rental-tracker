package com.rentaltracker.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BcryptPasswordHasherTest {

    private final PasswordHasher hasher = new BcryptPasswordHasher(BcryptPasswordHasher.MIN_COST);

    @Test
    void theHashIsNotThePasswordAndLooksLikeBcrypt() {
        String hash = hasher.hash("correct horse");

        assertNotEquals("correct horse", hash);
        assertFalse(hash.contains("correct horse"));
        assertTrue(hash.startsWith("$2"), "bcrypt hashes start with $2: " + hash);
    }

    @Test
    void theSamePasswordHashesDifferentlyEachTime() {
        assertNotEquals(hasher.hash("same"), hasher.hash("same"));
    }

    @Test
    void theRightPasswordMatches() {
        assertTrue(hasher.matches("correct horse", hasher.hash("correct horse")));
    }

    @Test
    void aWrongPasswordDoesNotMatch() {
        assertFalse(hasher.matches("wrong", hasher.hash("correct horse")));
    }

    @Test
    void matchingIsCaseSensitive() {
        assertFalse(hasher.matches("Correct Horse", hasher.hash("correct horse")));
    }

    @Test
    void aHashMadeAtOneCostStillVerifiesAtAnother() {
        String hash = new BcryptPasswordHasher(4).hash("secret");

        assertTrue(new BcryptPasswordHasher(6).matches("secret", hash));
    }

    @Test
    void missingInputsNeverMatch() {
        String hash = hasher.hash("x");

        assertFalse(hasher.matches(null, hash));
        assertFalse(hasher.matches("x", null));
        assertFalse(hasher.matches("x", ""));
        assertFalse(hasher.matches("x", "   "));
    }

    @Test
    void emptyPasswordsAreRefusedWhenHashing() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(""));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }

    @Test
    void theCostMustBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> new BcryptPasswordHasher(3));
        assertThrows(IllegalArgumentException.class, () -> new BcryptPasswordHasher(32));
    }
}
