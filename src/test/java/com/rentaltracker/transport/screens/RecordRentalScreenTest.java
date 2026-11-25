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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.SqliteTransactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.UserService;
import com.rentaltracker.transport.Display;
import com.rentaltracker.service.Session;
import com.rentaltracker.transport.Prompter;
import com.rentaltracker.transport.exception.InputClosedException;

class RecordRentalScreenTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-16T14:30:45Z"), ZoneId.of("UTC"));

    @TempDir Path tempDir;
    Database db;
    RentalService rentals;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        rentals = new RentalService(new ItemRepository(db.connection()), new RentalRepository(db),
                new UserService(new UserRepository(db)), new SqliteTransactor(db), CLOCK);
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x')");   // 1 owner, 2 renter
    }

    @AfterEach
    void tearDown() { db.close(); }

    private RecordRentalScreen screen(String input) {
        Prompter prompter = new Prompter(new ByteArrayInputStream(input.getBytes(UTF_8)),
                new PrintStream(buffer, true, UTF_8));
        return new RecordRentalScreen(prompter, rentals);
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

    private long addItem(String name, String status) throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
           + "VALUES (1, '" + name + "', '6-step ladder', 5, '" + status + "')");
        return Long.parseLong(query("SELECT last_insert_rowid()"));
    }

    private String rentalCount() throws SQLException { return query("SELECT count(*) FROM rentals"); }

    @Test
    void anEmptyListSaysSoAndNeverAsksForInput() {
        screen("").run(new Session(1, "tester"));

        assertTrue(output().contains("No items are available to rent."));
    }

    @Test
    void onlyAvailableItemsAreListedWithTheirCost() throws SQLException {
        addItem("Ladder", "available");
        addItem("Drill", "rented");
        addItem("Tent", "unlisted");

        screen("2\n").run(new Session(1, "tester"));   // one row, so Back is 2

        assertTrue(output().contains("1) " + Display.row("Ladder", "5 per day")));
        assertFalse(output().contains("Drill"));
        assertFalse(output().contains("Tent"));
    }

    @Test
    void theDetailViewShowsTheItemAndTheRentOption() throws SQLException {
        addItem("Ladder", "available");

        screen("1\n2\n2\n").run(new Session(1, "tester"));   // open it, back to the list, back to the menu

        assertTrue(output().contains("=== Ladder ==="));
        assertTrue(output().contains("6-step ladder"));
        assertTrue(output().contains("1) Rent this item"));
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
    void goingBackFromTheDetailRecordsNothing() throws SQLException {
        long id = addItem("Ladder", "available");

        screen("1\n2\n2\n").run(new Session(1, "tester"));

        assertEquals("0", rentalCount());
        assertEquals("available", query("SELECT status FROM listed_items WHERE item_id = " + id));
    }

    @Test
    void rentingRecordsTheRentalFlipsTheItemAndPrintsTheEndTime() throws SQLException {
        long id = addItem("Ladder", "available");

        // open, rent, renter, days. The list is then empty, so no further input is needed
        screen("1\n1\nmeelis\n3\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Rented Ladder to meelis until 2026-06-19 14:30."));
        assertEquals("rented", query("SELECT status FROM listed_items WHERE item_id = " + id));
        assertEquals("1", rentalCount());
        assertEquals("2026-06-19 14:30", query("SELECT end_time FROM rentals"));
        assertEquals("2", query("SELECT renter_id FROM rentals"));   // meelis reused
        assertEquals("2", query("SELECT count(*) FROM users"));
    }

    @Test
    @Disabled("single-owner design, replaced by browse-and-rent")
    void aNewRenterNameCreatesTheUser() throws SQLException {
        addItem("Ladder", "available");

        screen("1\n1\nnewperson\n2\n").run(new Session(1, "tester"));

        assertEquals("3", query("SELECT count(*) FROM users"));
        assertEquals("newperson", query("SELECT username FROM users WHERE id = 3"));
    }

    @Test
    void aBlankRenterNameIsAskedAgain() throws SQLException {
        addItem("Ladder", "available");

        screen("1\n1\n\n   \nmeelis\n3\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Please enter a value."));
        assertEquals("1", rentalCount());
    }

    @Test
    void aNonNumericDayCountIsAskedAgain() throws SQLException {
        addItem("Ladder", "available");

        screen("1\n1\nmeelis\nabc\n3\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Please enter a whole number."));
        assertEquals("1", rentalCount());
    }

    @Test
    void anInvalidDayCountIsPrintedAndNothingIsRecorded() throws SQLException {
        long id = addItem("Ladder", "available");

        // 0 days is rejected by the service, the message is printed, the list comes back, Back is 2
        screen("1\n1\nmeelis\n0\n2\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Days must be between 1 and 365"));
        assertEquals("0", rentalCount());
        assertEquals("available", query("SELECT status FROM listed_items WHERE item_id = " + id));
    }

    @Test
    void anInvalidRenterNameIsPrintedAndNothingIsRecorded() throws SQLException {
        addItem("Ladder", "available");

        screen("1\n1\nab\n3\n2\n").run(new Session(1, "tester"));   // too short for a username

        assertTrue(output().contains("at least 3 characters"));
        assertEquals("0", rentalCount());
        assertEquals("2", query("SELECT count(*) FROM users"));
    }

    @Test
    void endOfInputExitsTheScreenWithAnException() throws SQLException {
        addItem("Ladder", "available");

        assertThrows(InputClosedException.class, () -> screen("").run(new Session(1, "tester")));
    }
}