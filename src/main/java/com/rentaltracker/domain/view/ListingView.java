package com.rentaltracker.domain.view;

/** An item someone else has up for rent, with its owner's username, as shown in Browse listings. */
public record ListingView(
        int itemId,
        String itemName,
        String description,
        int costPerDay,
        String ownerUsername
) {}
