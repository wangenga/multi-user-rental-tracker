package com.rentaltracker.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.rentaltracker.transport.exception.InputClosedException;

import static org.junit.jupiter.api.Assertions.*;

class ListMenuTest {

    private final Function<String, String> row = s -> "row " + s;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private ListMenu menuWithInput(String input) {
        Prompter prompter = new Prompter(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        return new ListMenu(prompter);
    }

    private String printed() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private List<String> items(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> "item" + i)
                .toList();
    }

    private Optional<String> choose(String input, List<String> items) {
        return menuWithInput(input).choose("T", "Nothing here.", items, row);
    }

    @Test
    void emptyList_returnsEmptyAndPrintsItsOwnMessage() {
        Optional<String> chosen = choose("", List.of());

        assertTrue(chosen.isEmpty());
        assertTrue(printed().contains("Nothing here."));
    }

    @Test
    void picksFirstItemByNumber() {
        assertEquals(Optional.of("item1"), choose("1\n", items(3)));
    }

    @Test
    void picksLastItemOnFirstPage() {
        assertEquals(Optional.of("item5"), choose("5\n", items(7)));
    }

    @Test
    void backReturnsEmpty() {
        // 3 rows, so Back is number 4
        assertTrue(choose("4\n", items(3)).isEmpty());
    }

    @Test
    void backIsNumberedAfterTheRows() {
        choose("4\n", items(3));

        assertTrue(printed().contains("4) Back to menu"));
    }

    @Test
    void shortListShowsNoPageControls() {
        choose("4\n", items(3));

        assertFalse(printed().contains("Next page"));
        assertFalse(printed().contains("Previous page"));
        assertFalse(printed().contains("(page"));
    }

    @Test
    void exactlyFiveItemsFitOnOnePage() {
        choose("6\n", items(5));

        assertFalse(printed().contains("Next page"));
        assertTrue(printed().contains("6) Back to menu"));
    }

    @Test
    void firstPageOfManyOffersNextThenBackAndNoPrevious() {
        choose("7\n", items(6));

        assertTrue(printed().contains("6) Next page"));
        assertTrue(printed().contains("7) Back to menu"));
        assertFalse(printed().contains("Previous page"));
    }

    @Test
    void nextPageThenPick() {
        // page 1 has rows 1-5 and Next is 6, page 2 holds item6 and item7 as rows 1 and 2
        assertEquals(Optional.of("item7"), choose("6\n2\n", items(7)));
    }

    @Test
    void secondPageRestartsTheRowNumbersAndNumbersItsControls() {
        choose("6\n4\n", items(7));   // next page, then Back

        assertTrue(printed().contains("(page 2 of 2)"));
        assertTrue(printed().contains("1) row item6"));
        assertTrue(printed().contains("3) Previous page"));
        assertTrue(printed().contains("4) Back to menu"));
        assertFalse(printed().contains("Next page\n3)"));
    }

    @Test
    void previousPageThenPick() {
        // page1 -> Next (6) -> Previous (3) -> pick row 2 (item2)
        assertEquals(Optional.of("item2"), choose("6\n3\n2\n", items(7)));
    }

    @Test
    void middlePageOffersNextThenPreviousThenBack() {
        choose("8\n", items(11).subList(0, 11));   // 11 items: page 1 has Next 6, Back 7
        // go to page 2 of 3, where the controls are Next 6, Previous 7, Back 8
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        Prompter prompter = new Prompter(
                new ByteArrayInputStream("6\n8\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(second, true, StandardCharsets.UTF_8));
        new ListMenu(prompter).choose("T", "Nothing here.", items(11), row);

        String text = second.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("(page 2 of 3)"));
        assertTrue(text.contains("6) Next page"));
        assertTrue(text.contains("7) Previous page"));
        assertTrue(text.contains("8) Back to menu"));
    }

    @Test
    void invalidNumber_reprompts() {
        // out of range, then valid
        assertEquals(Optional.of("item2"), choose("99\n2\n", items(3)));
        assertTrue(printed().contains("Please enter a number from 1 to 4."));
    }

    @Test
    void zeroAndNegativeNumbers_reprompt() {
        assertEquals(Optional.of("item1"), choose("0\n-3\n1\n", items(3)));
    }

    @Test
    void nonNumber_reprompts() {
        assertEquals(Optional.of("item3"), choose("x\n3\n", items(3)));
    }

    @Test
    void aControlThatIsNotShownIsRejected() {
        // on page 1 of 7 items the controls are Next 6 and Back 7, so 8 does not exist
        choose("8\n7\n", items(7));

        assertTrue(printed().contains("Please enter a number from 1 to 7."));
    }

    @Test
    void rendersRowText() {
        menuWithInput("2\n").choose("MyTitle", "Nothing here.", List.of("a"), row);

        assertTrue(printed().contains("=== MyTitle ==="));
        assertTrue(printed().contains("1) row a"));
        assertTrue(printed().contains("2) Back to menu"));
    }

    @Test
    void showsPageLabelWhenMultiplePages() {
        choose("7\n", items(6));

        assertTrue(printed().contains("(page 1 of 2)"));
    }

    @Test
    void endOfInputThrows() {
        assertThrows(InputClosedException.class, () -> choose("", items(3)));
    }
}