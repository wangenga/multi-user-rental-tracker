package com.rentaltracker.transport.screens;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.SqliteTransactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.UserService;
import com.rentaltracker.transport.Display;
import com.rentaltracker.service.Session;
import com.rentaltracker.transport.Prompter;
import com.rentaltracker.transport.exception.InputClosedException;

class InventoryScreenTest {

    @TempDir Path tempDir;
    Database db;
    ItemService items;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        items = new ItemService(new ItemRepository(db.connection()), new RentalRepository(db),
                new UserService(new UserRepository(db)), new SqliteTransactor(db));
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x'), ('marili', 'x')");   // 1 owner, 2 renter, 3 other owner
    }

    @AfterEach
    void tearDown() { db.close(); }

    private InventoryScreen screen(String input) {
        Prompter prompter = new Prompter(new ByteArrayInputStream(input.getBytes(UTF_8)),
                new PrintStream(buffer, true, UTF_8));
        return new InventoryScreen(prompter, items);
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

    private long addItem(int ownerId, String name, String status) throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
           + "VALUES (" + ownerId + ", '" + name + "', '6-step ladder', 5, '" + status + "')");
        return Long.parseLong(query("SELECT last_insert_rowid()"));
    }

    private void addActiveRental(long itemId) throws SQLException {
        exec("INSERT INTO rentals (item_id, renter_id, start_time, end_time) "
           + "VALUES (" + itemId + ", 2, '2026-06-10 09:00', '2026-06-13 09:00')");
    }

    private String status(long itemId) throws SQLException {
        return query("SELECT status FROM listed_items WHERE item_id = " + itemId);
    }

    @Test
    void anEmptyInventorySaysSoAndNeverAsksForInput() {
        screen("").run(new Session(1, "tester"));

        assertTrue(output().contains("You have not listed any items yet."));
    }

    @Test
    void theListShowsEveryStatusButOnlyTheOwnersItems() throws SQLException {
        addItem(1, "Ladder", "available");
        addItem(1, "Drill", "rented");
        addItem(1, "Tent", "unlisted");
        addItem(3, "Saw", "available");   // someone else's

        screen("4\n").run(new Session(1, "tester"));   // 3 rows, so Back is 4

        assertTrue(output().contains("1) Ladder"));
        assertTrue(output().contains("2) Drill"));
        assertTrue(output().contains("3) Tent"));
        assertTrue(output().contains("4) Back to menu"));
        assertTrue(output().contains("rented"));
        assertTrue(output().contains("unlisted"));
        assertFalse(output().contains("Saw"));
    }

    @Test
    void theDetailViewShowsTheItemFieldsAndTheOwner() throws SQLException {
        addItem(1, "Ladder", "available");

        screen("1\n2\n2\n").run(new Session(1, "tester"));   // open it, back to the list, back to the menu

        assertTrue(output().contains("=== Ladder ==="));
        assertTrue(output().contains("6-step ladder"));
        assertTrue(output().contains("liisa"));
        assertTrue(output().contains("1) Delist"));
        assertTrue(output().contains("2) Back to list"));
    }

    @Test
    void aBlankDescriptionIsShownAsADash() throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day) "
           + "VALUES (1, 'Ladder', '', 5)");

        screen("1\n2\n2\n").run(new Session(1, "tester"));

        assertTrue(output().contains(Display.row("description", "-")));
    }

    @Test
    void goingBackFromTheDetailLeavesTheStatusAlone() throws SQLException {
        long id = addItem(1, "Ladder", "available");

        screen("1\n2\n2\n").run(new Session(1, "tester"));

        assertEquals("available", status(id));
    }

    @Test
    void delistingAnAvailableItemMakesItUnlisted() throws SQLException {
        long id = addItem(1, "Ladder", "available");

        screen("1\n1\n2\n").run(new Session(1, "tester"));   // open, delist, then Back from the rebuilt list

        assertTrue(output().contains("Ladder is now unlisted."));
        assertEquals("unlisted", status(id));
    }

    @Test
    void delistingARentedItemWorksAndKeepsItsRentalActive() throws SQLException {
        long id = addItem(1, "Ladder", "rented");
        addActiveRental(id);

        screen("1\n1\n2\n").run(new Session(1, "tester"));

        assertEquals("unlisted", status(id));
        assertEquals("active", query("SELECT status FROM rentals WHERE item_id = " + id));
    }

    @Test
    void anUnlistedItemOffersRelistAndRelistsIt() throws SQLException {
        long id = addItem(1, "Tent", "unlisted");

        screen("1\n1\n2\n").run(new Session(1, "tester"));

        assertTrue(output().contains("1) Relist"));
        assertFalse(output().contains("1) Delist"));
        assertTrue(output().contains("Tent is now available."));
        assertEquals("available", status(id));
    }

    @Test
    void relistingAnItemStillOutOnARentalIsRefusedWithAMessage() throws SQLException {
        long id = addItem(1, "Ladder", "unlisted");
        addActiveRental(id);

        screen("1\n1\n2\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Confirm the return first"));
        assertEquals("unlisted", status(id));
    }

    @Test
    void theListIsShownAgainAfterAnActionWithTheNewStatus() throws SQLException {
        addItem(1, "Ladder", "available");

        screen("1\n1\n2\n").run(new Session(1, "tester"));   // delist, then the list comes back

        int firstList = output().indexOf("=== My inventory");
        int secondList = output().indexOf("=== My inventory", firstList + 1);
        assertTrue(secondList > firstList, "the list should be shown again");
        assertTrue(output().substring(secondList).contains("unlisted"));
    }

    @Test
    void sixItemsArePagedAndTheSixthIsReachableFromPageTwo() throws SQLException {
        for (int i = 1; i <= 6; i++) {
            addItem(1, "Item" + i, "available");
        }

        // Next page (6), open the only row on page 2 (1), back to the list (2), Back from page 1 (7)
        screen("6\n1\n2\n7\n").run(new Session(1, "tester"));

        assertTrue(output().contains("(page 1 of 2)"));
        assertTrue(output().contains("(page 2 of 2)"));
        assertTrue(output().contains("2) Previous page"));
        assertTrue(output().contains("=== Item6 ==="));
    }

    @Test
    void endOfInputExitsTheScreenWithAnException() throws SQLException {
        addItem(1, "Ladder", "available");

        assertThrows(InputClosedException.class, () -> screen("").run(new Session(1, "tester")));
    }
}