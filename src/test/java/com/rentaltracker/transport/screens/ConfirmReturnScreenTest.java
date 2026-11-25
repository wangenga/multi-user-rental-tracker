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

class ConfirmReturnScreenTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-17T09:05:30Z"), ZoneId.of("UTC"));

    @TempDir Path tempDir;
    Database db;
    RentalService rentals;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        rentals = new RentalService(new ItemRepository(db.connection()), new RentalRepository(db),
                new UserService(new UserRepository(db)), new SqliteTransactor(db), CLOCK);
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x'), ('marili', 'x')");   // 1 owner, 2 and 3 renters
    }

    @AfterEach
    void tearDown() { db.close(); }

    private ConfirmReturnScreen screen(String input) {
        Prompter prompter = new Prompter(new ByteArrayInputStream(input.getBytes(UTF_8)),
                new PrintStream(buffer, true, UTF_8));
        return new ConfirmReturnScreen(prompter, rentals);
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

    /** An item out on an active rental. Returns the rental id. */
    private long addRental(String itemName, String itemStatus, int renterId, String end) throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
           + "VALUES (1, '" + itemName + "', 'x', 5, '" + itemStatus + "')");
        long itemId = Long.parseLong(query("SELECT last_insert_rowid()"));
        exec("INSERT INTO rentals (item_id, renter_id, start_time, end_time) VALUES ("
           + itemId + ", " + renterId + ", '2026-06-16 14:30', '" + end + "')");
        return Long.parseLong(query("SELECT last_insert_rowid()"));
    }

    @Test
    void anEmptyListSaysSoAndNeverAsksForInput() {
        screen("").run(new Session(1, "tester"));

        assertTrue(output().contains("Nothing is out on rental."));
    }

    @Test
    void theListShowsItemAndRenterSortedByDueDate() throws SQLException {
        addRental("Ladder", "rented", 2, "2026-06-25 10:00");
        addRental("Drill", "rented", 3, "2026-06-18 10:00");

        screen("3\n").run(new Session(1, "tester"));   // two rows, so Back is 3

        int drill = output().indexOf("1) " + Display.row("Drill", "marili"));
        int ladder = output().indexOf("2) " + Display.row("Ladder", "meelis"));
        assertTrue(drill >= 0 && ladder > drill, "soonest due date should be first");
    }

    @Test
    void theDetailViewUsesTheDisplayFormats() throws SQLException {
        addRental("Ladder", "rented", 2, "2026-06-19 14:30");

        screen("1\n2\n2\n").run(new Session(1, "tester"));

        assertTrue(output().contains("=== Ladder ==="));
        assertTrue(output().contains(Display.row("renter", "meelis")));
        assertTrue(output().contains(Display.row("start", "2026-06-16 14:30")));
        assertTrue(output().contains(Display.row("end", "2026-06-19 14:30")));
        assertTrue(output().contains(Display.row("status", "active")));
        assertFalse(output().contains("T14:30"));
        assertFalse(output().contains("ACTIVE"));
        assertTrue(output().contains("1) Confirm return"));
    }

    @Test
    void goingBackFromTheDetailConfirmsNothing() throws SQLException {
        long rentalId = addRental("Ladder", "rented", 2, "2026-06-19 14:30");

        screen("1\n2\n2\n").run(new Session(1, "tester"));

        assertEquals("active", query("SELECT status FROM rentals WHERE rental_id = " + rentalId));
    }

    @Test
    void confirmingClosesTheRentalAndFreesTheItem() throws SQLException {
        long rentalId = addRental("Ladder", "rented", 2, "2026-06-19 14:30");

        screen("1\n1\n").run(new Session(1, "tester"));   // open, confirm, then the list is empty

        assertTrue(output().contains("Returned Ladder."));
        assertFalse(output().contains("stays unlisted"));
        assertEquals("closed", query("SELECT status FROM rentals WHERE rental_id = " + rentalId));
        assertEquals("2026-06-17 09:05", query("SELECT returned_at FROM rentals WHERE rental_id = " + rentalId));
        assertEquals("available", query("SELECT status FROM listed_items WHERE item_name = 'Ladder'"));
    }

    @Test
    void anItemDelistedWhileOutStaysUnlistedAndTheScreenSaysSo() throws SQLException {
        long rentalId = addRental("Ladder", "unlisted", 2, "2026-06-19 14:30");

        screen("1\n1\n").run(new Session(1, "tester"));

        assertTrue(output().contains("stays unlisted"));
        assertEquals("closed", query("SELECT status FROM rentals WHERE rental_id = " + rentalId));
        assertEquals("unlisted", query("SELECT status FROM listed_items WHERE item_name = 'Ladder'"));
    }

    @Test
    void theListIsShownAgainWithoutTheReturnedRental() throws SQLException {
        addRental("Ladder", "rented", 2, "2026-06-19 14:30");
        addRental("Drill", "rented", 3, "2026-06-25 10:00");

        screen("1\n1\n2\n").run(new Session(1, "tester"));   // return the first, then Back from the one-row list

        int secondList = output().indexOf("=== Confirm return", output().indexOf("=== Confirm return") + 1);
        assertTrue(secondList > 0);
        assertFalse(output().substring(secondList).contains("Ladder"));
        assertTrue(output().substring(secondList).contains("Drill"));
    }

    @Test
    void endOfInputExitsTheScreenWithAnException() throws SQLException {
        addRental("Ladder", "rented", 2, "2026-06-19 14:30");

        assertThrows(InputClosedException.class, () -> screen("").run(new Session(1, "tester")));
    }
}