package com.rentaltracker.service;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.service.exception.NotOwnerException;

/**
 * The one ownership rule, shared by every service: only an item's owner may change it,
 * or act on its rentals.
 *
 * The item passed in must have been loaded from the database by the service, never
 * handed over by a caller, so its owner_id can be trusted. The actor id comes from
 * the logged-in session.
 */
final class Ownership {

    private Ownership() {}

    /**
     * @param action what was attempted, for the log, e.g. "delist"
     * @param userMessage what the user is told, e.g. "You can only delist your own items."
     * @throws NotOwnerException if the actor is not the item's owner
     */
    static void requireOwner(ItemDomain item, int actorId, String action, String userMessage,
                             AppLogger logger) {
        if (item.getOwnerId() != actorId) {
            logger.warn(LogEvents.OWNERSHIP_DENIED,
                    "action", action, "actor_id", actorId, "item_id", item.getItemId());
            throw new NotOwnerException(userMessage);
        }
    }
}
