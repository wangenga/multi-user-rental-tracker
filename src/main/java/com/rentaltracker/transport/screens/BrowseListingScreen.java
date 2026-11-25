package com.rentaltracker.transport.screens;

import java.util.List;
import java.util.Optional;

import com.rentaltracker.domain.RentalDomain;
import com.rentaltracker.domain.view.ListingView;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.Session;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.service.exception.ValidationException;
import com.rentaltracker.transport.Display;
import com.rentaltracker.transport.ListMenu;
import com.rentaltracker.transport.Prompter;

/**
 * Every available item that isn't yours, five per page. Picking one opens its details,
 * and from there you can rent it for a number of days with yourself as the renter.
 */
public class BrowseListingScreen {

    private final Prompter prompter;
    private final ListMenu listMenu;
    private final RentalService rentals;

    public BrowseListingScreen(Prompter prompter, RentalService rentals) {
        this.prompter = prompter;
        this.listMenu = new ListMenu(prompter);
        this.rentals = rentals;
    }

    /** Reloaded after every visit to a listing, so a rented item drops off the list. */
    public void run(Session session) {
        while (true) {
            List<ListingView> listings = rentals.getListings(session.userId());
            Optional<ListingView> picked = listMenu.choose(
                    "Listings",
                    "Nothing is available to rent right now.",
                    listings,
                    BrowseListingScreen::row);
            if (picked.isEmpty()) {
                return;
            }
            showDetail(picked.get(), session);
        }
    }

    /** Name, price, owner in three columns: "Pressure washer   8/day   meelis". */
    static String row(ListingView listing) {
        return String.format("%-18s %6s   %s",
                listing.itemName(), listing.costPerDay() + "/day", listing.ownerUsername());
    }

    private void showDetail(ListingView listing, Session session) {
        prompter.println();
        prompter.println("=== " + listing.itemName() + " ===");
        prompter.println(Display.row("description", Display.orDash(listing.description())));
        prompter.println(Display.row("cost per day", listing.costPerDay()));
        prompter.println(Display.row("owner", listing.ownerUsername()));
        prompter.println("1) Rent this item");
        prompter.println("2) Back to list");

        if (prompter.askChoice("> ", 1, 2) == 2) {
            return;
        }
        rent(listing, session);
    }

    /** A refused rental is shown and the user goes back to the list. */
    private void rent(ListingView listing, Session session) {
        int days = prompter.askInt("Days: ");
        try {
            RentalDomain rental = rentals.rent(session.userId(), listing.itemId(), days);
            prompter.println("Rented " + listing.itemName() + " from " + listing.ownerUsername()
                    + " until " + Display.time(rental.endTime()) + ".");
        } catch (ValidationException | BusinessRuleException e) {
            prompter.println(e.getMessage());
        } catch (NotFoundException e) {
            // removed between browsing and renting
            prompter.println("That listing is no longer available.");
        }
    }
}
