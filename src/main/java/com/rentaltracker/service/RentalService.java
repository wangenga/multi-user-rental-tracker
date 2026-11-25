package com.rentaltracker.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.RentalDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.domain.enums.RentalStatus;
import com.rentaltracker.domain.view.ActiveRentalView;
import com.rentaltracker.domain.view.ListingView;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.repository.exception.SqlExceptionTranslator;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.service.exception.NotOwnerException;
import com.rentaltracker.service.exception.ValidationException;
import com.rentaltracker.service.validation.RentalValidator;

public class RentalService {

    private final ItemRepository items;
    private final RentalRepository rentals;
    private final Transactor tx;
    private final Clock clock;
    private final AppLogger logger;

    public RentalService(ItemRepository items, RentalRepository rentals, Transactor tx, Clock clock,
                         AppLogger logger) {
        this.items = items;
        this.rentals = rentals;
        this.tx = tx;
        this.clock = clock;
        this.logger = logger;
    }

    // ---------- reads ----------

    /** Everything the viewer could rent right now: available items that belong to someone else. */
    public List<ListingView> getListings(int viewerId) {
        return items.findListingsExcludingOwner(viewerId);
    }

    /** Active rentals on the owner's items, soonest due date first. */
    public List<RentalDomain> getActiveRentals(int ownerId) {
        return rentals.findActiveByOwner(ownerId);
    }

    public List<ActiveRentalView> getActiveRentalViews(int ownerId) {
        return rentals.findActiveViewsByOwner(ownerId);
    }

    /**
     * One rental for its detail screen. Only the owner of the rented item may see it,
     * because it shows who rented what and when.
     *
     * @throws NotFoundException if there is no such rental
     * @throws NotOwnerException if actorId does not own the rented item
     */
    public ActiveRentalView getRentalView(int actorId, long rentalId) {
        RentalDomain rental = rentals.findById(rentalId);
        ItemDomain item = requireItem((int) rental.itemId());
        Ownership.requireOwner(item, actorId, "view_rental",
                "You can only view rentals of your own items.", logger);
        return rentals.findViewById(rentalId);
    }

    // ---------- rent ----------

    /**
     * The logged-in user rents someone else's item for a number of days.
     * Every rule is checked here, not trusted to the screen: the browse list already hides
     * your own items, but a caller that skips the list is still refused.
     *
     * @throws ValidationException   if days is out of range
     * @throws NotFoundException     if the item does not exist (or was deleted after browsing)
     * @throws BusinessRuleException if it is your own item, already rented, or unlisted
     */
    public RentalDomain rent(int renterId, int itemId, int days) {
        RentalValidator.validateDays(days);

        RentalDomain rental = tx.inTransaction(() -> {
            ItemDomain item = requireItem(itemId);

            if (item.getOwnerId() == renterId) {
                throw new BusinessRuleException("You cannot rent your own item: " + item.getItemName());
            }
            if (item.getStatus() == ItemStatus.rented) {
                throw new BusinessRuleException("Item is already rented: " + item.getItemName());
            }
            if (item.getStatus() == ItemStatus.unlisted) {
                throw new BusinessRuleException("Item is unlisted and cannot be rented: " + item.getItemName());
            }

            LocalDateTime start = now();
            LocalDateTime end = start.plusDays(days);

            long rentalId = rentals.insert(
                    new RentalDomain(0, itemId, renterId, start, end, null, RentalStatus.ACTIVE));

            item.setStatus(ItemStatus.rented);
            saveItem(item);

            return rentals.findById(rentalId);
        });

        // logged only once the transaction has committed, so the log never claims a rental that was rolled back
        logger.info(LogEvents.RENTAL_CREATED,
                "rental_id", rental.id(), "item_id", rental.itemId(), "renter_id", rental.renterId());
        return rental;
    }

    // ---------- confirm a return ----------

    /**
     * Closes the rental. The item goes back to available, unless it was delisted while out.
     * Only the owner of the rented item may confirm it: the person who got it back, not the renter.
     *
     * @throws NotFoundException     if there is no such rental
     * @throws NotOwnerException     if actorId does not own the rented item
     * @throws BusinessRuleException if the rental is already closed
     */
    public ItemDomain confirmReturn(int actorId, long rentalId) {
        return tx.inTransaction(() -> {
            RentalDomain rental = rentals.findById(rentalId);
            ItemDomain item = requireItem((int) rental.itemId());
            Ownership.requireOwner(item, actorId, "confirm_return",
                    "You can only confirm returns of your own items.", logger);

            if (rental.status() != RentalStatus.ACTIVE) {
                throw new BusinessRuleException("Rental is already closed: " + rentalId);
            }

            rentals.close(rentalId, now());

            if (item.getStatus() == ItemStatus.rented) {
                item.setStatus(ItemStatus.available);
                saveItem(item);
            }
            // unlisted stays unlisted
            return item;
        });
    }

    // ---------- helpers ----------

    /** Minute precision, because the database stores yyyy-MM-dd HH:mm. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }

    private ItemDomain requireItem(int itemId) {
        return db("find item", () -> items.findByItemId(itemId))
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
    }

    private void saveItem(ItemDomain item) {
        db("update item", () -> {
            items.update(item);
            return null;
        });
    }

    // ItemRepository throws checked SQLException. Delete this wrapper once it translates its own errors.
    private interface SqlCall<T> {
        T run() throws SQLException;
    }

    private <T> T db(String operation, SqlCall<T> call) {
        try {
            return call.run();
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate(operation, e);
        }
    }
}
