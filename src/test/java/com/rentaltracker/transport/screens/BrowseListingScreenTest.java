package com.rentaltracker.transport.screens;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.rentaltracker.domain.view.ListingView;
import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.logging.NoOpLogger;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.SqliteTransactor;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.Session;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.transport.Prompter;
import com.rentaltracker.transport.exception.InputClosedException;

class BrowseListingScreenTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-16T14:30:45Z"), ZoneId.of("UTC"));

    // liisa (1) is logged in and browsing; meelis (2) and marili (3) have things to lend
    static final Session LIISA = new Session(1, "liisa");

    @TempDir Path tempDir;
    Database db;
    RentalService rentals;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        rentals = new RentalService(new ItemRepository(db.connection()), new RentalRepository(db),
                new SqliteTransactor(db), CLOCK, new NoOpLogger());
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x'), ('marili', 'x')");
    }

    @AfterEach
    void tearDown() { db.close(); }

    private BrowseListingScreen screen(String input) {
        return screen(input, rentals);
    }

    private BrowseListingScreen screen(String input, RentalService service) {
        Prompter prompter = new Prompter(new ByteArrayInputStream(input.getBytes(UTF_8)),
                new PrintStream(buffer, true, UTF_8));
        return new BrowseListingScreen(prompter, service);
    }

    private String output() { return buffer.toString(UTF_8); }

    private void exec(String sql) throws SQLException {
        try (Statement st = db.connection().createStatement()) { st.execute(sql); }
    }

    private String query(String sql) throws SQLException {
        try (Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private long addItem(int ownerId, String name, int cost, String status) throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
           + "VALUES (" + ownerId + ", '" + name + "', 'Good condition', " + cost + ", '" + status + "')");
        return Long.parseLong(query("SELECT last_insert_rowid()"));
    }

    private String rentalCount() throws SQLException {
        return query("SELECT count(*) FROM rentals");
    }

    // ---------- the list ----------

    @Test
    void anEmptyListSaysSoAndReturnsToTheMenu() throws SQLException {
        screen("").run(LIISA);

        assertTrue(output().contains("Nothing is available to rent right now."));
    }

    @Test
    void theListShowsOtherPeoplesAvailableItemsWithPriceAndOwner() throws SQLException {
        addItem(2, "Pressure washer", 8, "available");
        addItem(3, "Tent", 6, "available");

        screen("3\n").run(LIISA);   // two rows, so Back is 3

        String out = output();
        assertTrue(out.contains("=== Listings ==="));
        assertTrue(out.contains("1) " + BrowseListingScreen.row(
                new ListingView(0, "Pressure washer", "", 8, "meelis"))), out);
        assertTrue(out.contains("2) " + BrowseListingScreen.row(
                new ListingView(0, "Tent", "", 6, "marili"))), out);
        assertTrue(out.contains("3) Back to menu"));
    }

    @Test
    void theViewersOwnItemsAndUnavailableItemsAreNotListed() throws SQLException {
        addItem(1, "My ladder", 5, "available");
        addItem(2, "Drill", 4, "rented");
        addItem(2, "Sander", 3, "unlisted");

        screen("").run(LIISA);

        assertFalse(output().contains("My ladder"));
        assertFalse(output().contains("Drill"));
        assertFalse(output().contains("Sander"));
        assertTrue(output().contains("Nothing is available to rent right now."));
    }

    @Test
    void moreThanFiveListingsArePaged() throws SQLException {
        for (int i = 1; i <= 6; i++) {
            addItem(2, "Item " + i, 10 - i, "available");
        }

        screen("7\n").run(LIISA);   // 5 rows, Next page is 6, Back is 7

        assertTrue(output().contains("=== Listings (page 1 of 2) ==="));
        assertTrue(output().contains("6) Next page"));
        assertFalse(output().contains("Item 6"));
    }

    @Test
    void rowLinesUpNamePriceAndOwnerInColumns() {
        String row = BrowseListingScreen.row(new ListingView(1, "Drill", "", 4, "siim"));

        assertEquals(String.format("%-18s %6s   %s", "Drill", "4/day", "siim"), row);
    }

    // ---------- the detail view ----------

    @Test
    void pickingAListingShowsItsDetails() throws SQLException {
        addItem(2, "Saw", 3, "available");

        screen("1\n2\n2\n").run(LIISA);   // open it, back to the list, back to the menu

        String out = output();
        assertTrue(out.contains("=== Saw ==="));
        assertTrue(out.contains("Good condition"));
        assertTrue(out.contains("meelis"));
        assertTrue(out.contains("1) Rent this item"));
        assertTrue(out.contains("2) Back to list"));
    }

    @Test
    void goingBackFromTheDetailRentsNothing() throws SQLException {
        long itemId = addItem(2, "Saw", 3, "available");

        screen("1\n2\n2\n").run(LIISA);

        assertEquals("0", rentalCount());
        assertEquals("available", query("SELECT status FROM listed_items WHERE item_id = " + itemId));
    }

    // ---------- renting ----------

    @Test
    void rentingRecordsTheLoggedInUserAsRenterAndTheItemLeavesTheList() throws SQLException {
        long itemId = addItem(2, "Saw", 3, "available");

        screen("1\n1\n3\n").run(LIISA);   // open, rent, 3 days; then the list is empty

        assertEquals("1", rentalCount());
        assertEquals("1", query("SELECT renter_id FROM rentals"));
        assertEquals("rented", query("SELECT status FROM listed_items WHERE item_id = " + itemId));
        assertTrue(output().contains("Rented Saw from meelis until 2026-06-19 14:30."), output());
        assertTrue(output().contains("Nothing is available to rent right now."));
    }

    @Test
    void anInvalidNumberOfDaysShowsTheValidatorMessageAndRentsNothing() throws SQLException {
        addItem(2, "Saw", 3, "available");

        screen("1\n1\n0\n2\n").run(LIISA);   // open, rent, 0 days, then Back from the list

        assertTrue(output().contains("Rental duration must be at least 1 day"));
        assertEquals("0", rentalCount());
    }

    @Test
    void daysThatAreNotANumberAreAskedForAgain() throws SQLException {
        addItem(2, "Saw", 3, "available");

        screen("1\n1\nabc\n2\n").run(LIISA);

        assertTrue(output().contains("Please enter a whole number."));
        assertEquals("1", rentalCount());
    }

    @Test
    void aListingRemovedAfterBrowsingGivesAReadableMessage() {
        RentalService service = mock(RentalService.class);
        when(service.getListings(1))
                .thenReturn(List.of(new ListingView(7, "Saw", "", 3, "meelis")))
                .thenReturn(List.of());
        when(service.rent(1, 7, 2)).thenThrow(new NotFoundException("Item not found: 7"));

        screen("1\n1\n2\n", service).run(LIISA);

        assertTrue(output().contains("That listing is no longer available."));
        assertFalse(output().contains("Item not found: 7"));
    }

    @Test
    void aListingRentedBySomeoneElseInTheMeantimeShowsTheReason() {
        RentalService service = mock(RentalService.class);
        when(service.getListings(1))
                .thenReturn(List.of(new ListingView(7, "Saw", "", 3, "meelis")))
                .thenReturn(List.of());
        when(service.rent(1, 7, 2)).thenThrow(new BusinessRuleException("Item is already rented: Saw"));

        screen("1\n1\n2\n", service).run(LIISA);

        assertTrue(output().contains("Item is already rented: Saw"));
    }

    @Test
    void endOfInputIsPassedUpSoTheMenuCanExit() throws SQLException {
        addItem(2, "Saw", 3, "available");

        assertThrows(InputClosedException.class, () -> screen("").run(LIISA));
    }
}
