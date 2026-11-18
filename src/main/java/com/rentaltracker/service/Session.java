package com.rentaltracker.service;

import com.rentaltracker.domain.User;

/** Who is logged in. Created at login, owned by the menu, never a static global. */
public record Session(int userId, String username) {

    public static Session of(User user) {
        return new Session((int) user.getId(), user.getUsername());
    }
}
