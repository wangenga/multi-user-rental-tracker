package com.rentaltracker.transport.screens;

import java.util.List;
import java.util.Optional;

import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.domain.view.ActiveRentalView;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.transport.Display;
import com.rentaltracker.transport.ListMenu;
import com.rentaltracker.transport.Prompter;

public class ConfirmReturnScreen {

    private final Prompter prompter;
    private final ListMenu listMenu;
    private final RentalService rentals;

    public ConfirmReturnScreen(Prompter prompter, RentalService rentals) {
        this.prompter = prompter;
        this.listMenu = new ListMenu(prompter);
        this.rentals = rentals;
    }

    public void run(int ownerId) {
        while (true) {
            List<ActiveRentalView> active = rentals.getActiveRentalViews(ownerId);
            Optional<ActiveRentalView> picked = listMenu.choose(
                    "Confirm return",
                    "Nothing is out on rental.",
                    active,
                    view -> Display.row(view.itemName(), view.renterUsername()));
            if (picked.isEmpty()) {
                return;
            }
            showDetail(picked.get().rentalId());
        }
    }

    private void showDetail(long rentalId) {
        ActiveRentalView view = rentals.getRentalView(rentalId);

        prompter.println();
        prompter.println("=== " + view.itemName() + " ===");
        prompter.println(Display.row("renter", view.renterUsername()));
        prompter.println(Display.row("start", Display.time(view.startTime())));
        prompter.println(Display.row("end", Display.time(view.endTime())));
        prompter.println(Display.row("status", view.status().name().toLowerCase()));
        prompter.println("1) Confirm return");
        prompter.println("2) Back to list");

        if (prompter.askChoice("> ", 1, 2) == 2) {
            return;
        }

        try {
            var item = rentals.confirmReturn(rentalId);
            prompter.println("Returned " + view.itemName() + ".");
            if (item.getStatus() == ItemStatus.unlisted) {
                prompter.println("It stays unlisted because it was delisted while out.");
            }
        } catch (BusinessRuleException e) {
            prompter.println(e.getMessage());
        }
    }
}