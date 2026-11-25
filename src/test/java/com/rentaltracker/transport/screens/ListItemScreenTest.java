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
import com.rentaltracker.service.Session;
import com.rentaltracker.transport.Prompter;
import com.rentaltracker.transport.exception.InputClosedException;

class ListItemScreenTest {

    @TempDir Path tempDir;
    Database db;
    ItemService items;
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        items = new ItemService(new ItemRepository(db.connection()), new RentalRepository(db),
                new UserService(new UserRepository(db)), new SqliteTransactor(db));
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x')");   // 1 and 2
    }

    @AfterEach
    void tearDown() { db.close(); }

    private ListItemScreen screen(String input) {
        Prompter prompter = new Prompter(new ByteArrayInputStream(input.getBytes(UTF_8)),
                new PrintStream(buffer, true, UTF_8));
        return new ListItemScreen(prompter, items);
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

    private String itemCount() throws SQLException { return query("SELECT count(*) FROM listed_items"); }

    @Test
    void listingAnItemStoresItAsAvailableAndConfirms() throws SQLException {
        screen("Ladder\n6-step ladder\n5\n").run(new Session(1, "tester"));

        assertTrue(output().contains("=== List an item ==="));
        assertTrue(output().contains("Listed Ladder at 5 per day."));
        assertEquals("1", itemCount());
        assertEquals("Ladder", query("SELECT item_name FROM listed_items"));
        assertEquals("6-step ladder", query("SELECT description FROM listed_items"));
        assertEquals("5", query("SELECT cost_per_day FROM listed_items"));
        assertEquals("available", query("SELECT status FROM listed_items"));
        assertEquals("1", query("SELECT owner_id FROM listed_items"));
    }

    @Test
    void theItemBelongsToTheOwnerPassedToTheScreen() throws SQLException {
        screen("Saw\nHand saw\n8\n").run(new Session(2, "tester"));

        assertEquals("2", query("SELECT owner_id FROM listed_items"));
    }

    @Test
    void aBlankDescriptionIsAllowedAndStoredAsEmptyText() throws SQLException {
        screen("Ladder\n\n5\n").run(new Session(1, "tester"));

        assertEquals("", query("SELECT description FROM listed_items"));
        assertTrue(output().contains("Listed Ladder at 5 per day."));
    }

    @Test
    void aBlankNameIsAskedAgain() throws SQLException {
        screen("\n   \nLadder\nx\n5\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Please enter a value."));
        assertEquals("1", itemCount());
        assertEquals("Ladder", query("SELECT item_name FROM listed_items"));
    }

    @Test
    void aNonNumericCostIsAskedAgain() throws SQLException {
        screen("Ladder\nx\nabc\n1.5\n5\n").run(new Session(1, "tester"));

        assertTrue(output().contains("Please enter a whole number."));
        assertEquals("5", query("SELECT cost_per_day FROM listed_items"));
    }

    @Test
    void aCostBelowOneIsRejectedByTheServiceAndNothingIsStoredOrConfirmed() throws SQLException {
        // the screen does not catch it, MainMenu reports it
        assertThrows(IllegalArgumentException.class, () -> screen("Ladder\nx\n0\n").run(new Session(1, "tester")));

        assertEquals("0", itemCount());
        assertFalse(output().contains("Listed "));
    }

    @Test
    void anUnknownOwnerIsRejectedAndNothingIsStored() throws SQLException {
        assertThrows(RuntimeException.class, () -> screen("Ladder\nx\n5\n").run(new Session(99, "tester")));

        assertEquals("0", itemCount());
        assertFalse(output().contains("Listed "));
    }

    @Test
    void endOfInputPartWayThroughStoresNothing() throws SQLException {
        assertThrows(InputClosedException.class, () -> screen("Ladder\n").run(new Session(1, "tester")));
        assertThrows(InputClosedException.class, () -> screen("").run(new Session(1, "tester")));

        assertEquals("0", itemCount());
    }
}