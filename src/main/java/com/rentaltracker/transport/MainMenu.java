package com.rentaltracker.transport;

import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.service.Session;
import com.rentaltracker.transport.exception.InputClosedException;
import com.rentaltracker.transport.screens.ConfirmReturnScreen;
import com.rentaltracker.transport.screens.InventoryScreen;
import com.rentaltracker.transport.screens.ListItemScreen;
import com.rentaltracker.transport.screens.BrowseListingScreen;
import com.rentaltracker.transport.screens.StartupScreen;

public class MainMenu {

    private static final int EXIT = 5;

    private final Prompter prompter;
    private final StartupScreen startup;
    private final ListItemScreen listItem;
    private final InventoryScreen inventory;
    private final BrowseListingScreen browseListings;
    private final ConfirmReturnScreen confirmReturn;
    private final ErrorHandler errors;

    public MainMenu(Prompter prompter, AppLogger logger, StartupScreen startup, ListItemScreen listItem,
                    InventoryScreen inventory, BrowseListingScreen browseListings,
                    ConfirmReturnScreen confirmReturn) {
        this.prompter = prompter;
        this.startup = startup;
        this.listItem = listItem;
        this.inventory = inventory;
        this.browseListings = browseListings;
        this.confirmReturn = confirmReturn;
        this.errors = new ErrorHandler(prompter, logger);
    }

    /**
     * Logs a user in, then runs the menu for them until they exit or the input ends.
     * The session lives only in this method, so exiting is logging out: the next
     * launch starts back at the login prompt.
     */
    public void run() {
        try {
            Session session = startup.run();
            while (true) {
                showMenu();
                int choice = prompter.askChoice("> ", 1, EXIT);
                if (choice == EXIT) {
                    prompter.println("Logged out. Goodbye.");
                    return;
                }
                runScreen(choice, session);
            }
        } catch (InputClosedException e) {
            prompter.println();
            prompter.println("Goodbye.");
        } catch (RuntimeException e) {
            // nobody is logged in yet, so there is no menu to go back to
            errors.handle("startup", e);
        }
    }

    private void showMenu() {
        prompter.println();
        prompter.println("=== Rental tracker ===");
        prompter.println("1) List an item");
        prompter.println("2) View my inventory");
        prompter.println("3) Browse listings");
        prompter.println("4) Confirm a return");
        prompter.println("5) Exit");
    }

    /** Any failure inside a screen is shown, logged, and the user lands back on the menu. */
    private void runScreen(int choice, Session session) {
        String operation = switch (choice) {
            case 1 -> "list_item";
            case 2 -> "inventory";
            case 3 -> "browse_listings";
            default -> "confirm_return";
        };
        try {
            switch (choice) {
                case 1 -> listItem.run(session);
                case 2 -> inventory.run(session);
                case 3 -> browseListings.run(session);
                default -> confirmReturn.run(session);
            }
        } catch (InputClosedException e) {
            throw e;   // not an error: the input ended, so run() says goodbye
        } catch (RuntimeException e) {
            errors.handle(operation, e);
        }
    }
}