package com.rentaltracker.service.exception;

/** A failed login. One message for both an unknown user and a wrong password. */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}