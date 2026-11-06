package com.rentaltracker.transport;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A numbered list, 5 per page. Rows are numbered from 1 on every page, and the controls
 * that apply (next, previous, back) are numbered after them, as in the spec example.
 */
public class ListMenu {

    private final Prompter prompter;

    public ListMenu(Prompter prompter) {
        this.prompter = prompter;
    }

    /** @return the chosen item, or empty if the list is empty or the user chose Back */
    public <T> Optional<T> choose(String title, String emptyMessage, List<T> items,
                                  Function<T, String> row) {
        if (items.isEmpty()) {
            prompter.println(emptyMessage);
            return Optional.empty();
        }

        PaginatedList<T> pages = new PaginatedList<>(items);
        while (true) {
            List<T> current = pages.currentPage();
            int rows = current.size();
            int next = pages.hasNext() ? rows + 1 : 0;                        // 0 means not shown
            int previous = pages.hasPrevious() ? rows + (pages.hasNext() ? 2 : 1) : 0;
            int back = rows + 1 + (pages.hasNext() ? 1 : 0) + (pages.hasPrevious() ? 1 : 0);

            render(title, pages, current, row, next, previous, back);
            int choice = prompter.askChoice("> ", 1, back);

            if (choice <= rows) {
                return Optional.of(current.get(choice - 1));
            }
            if (choice == next) {
                pages.next();
                continue;
            }
            if (choice == previous) {
                pages.previous();
                continue;
            }
            return Optional.empty();
        }
    }

    private <T> void render(String title, PaginatedList<T> pages, List<T> current,
                            Function<T, String> row, int next, int previous, int back) {
        String pageLabel = pages.pageCount() > 1
                ? " (page " + pages.pageNumber() + " of " + pages.pageCount() + ")"
                : "";
        prompter.println();
        prompter.println("=== " + title + pageLabel + " ===");
        for (int i = 0; i < current.size(); i++) {
            prompter.println((i + 1) + ") " + row.apply(current.get(i)));
        }
        if (next > 0) prompter.println(next + ") Next page");
        if (previous > 0) prompter.println(previous + ") Previous page");
        prompter.println(back + ") Back to menu");
    }
}