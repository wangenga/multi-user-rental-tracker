package com.rentaltracker.transport.screens;

<<<<<<< Updated upstream
import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.transport.Prompter;
import org.junit.jupiter.api.Test;
=======
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
>>>>>>> Stashed changes

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
<<<<<<< Updated upstream
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListItemScreenTest {

    private Prompter prompter(String input, ByteArrayOutputStream out) {
        return new Prompter(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    /*
     * ItemDomain(int itemId, int ownerId, String itemName, String description,
     *            int costPerDay, ItemStatus status, LocalDateTime createdAt)
     */
    private ItemDomain item(int id, int ownerId, String name, String desc, int cost, ItemStatus status) {
        return new ItemDomain(id, ownerId, name, desc, cost, status,
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void happyPath_callsCreateItemWithOwnerAndInputs() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(1, "Drill", "Cordless", 15))
                .thenReturn(item(42, 1, "Drill", "Cordless", 15, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new ListItemScreen(prompter("Drill\nCordless\n15\n", out), items).run(1);

        verify(items).createItem(1, "Drill", "Cordless", 15);
    }

    @Test
    void happyPath_printsListingMessage() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(1, "Drill", "Cordless", 15))
                .thenReturn(item(42, 1, "Drill", "Cordless", 15, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new ListItemScreen(prompter("Drill\nCordless\n15\n", out), items).run(1);

        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("=== List an item ==="));
        assertTrue(printed.contains("Listed Drill at 15 per day."));
    }

    @Test
    void header_isPrintedBeforePrompting() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(item(42, 1, "Drill", "", 15, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new ListItemScreen(prompter("Drill\n\n15\n", out), items).run(1);

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("=== List an item ==="));
    }

    @Test
    void emptyDescription_isPassedThroughAsEmptyString() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(1, "Drill", "", 15))
                .thenReturn(item(42, 1, "Drill", "", 15, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // name, blank description, cost
        new ListItemScreen(prompter("Drill\n\n15\n", out), items).run(1);

        verify(items).createItem(1, "Drill", "", 15);
    }

    @Test
    void ownerId_isForwardedToCreateItem() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(7, "Saw", "Hand saw", 8))
                .thenReturn(item(42, 7, "Saw", "Hand saw", 8, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new ListItemScreen(prompter("Saw\nHand saw\n8\n", out), items).run(7);

        verify(items).createItem(7, "Saw", "Hand saw", 8);
    }

    @Test
    void nonBlankName_isRetriedUntilNonBlank() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(eq(1), eq("Drill"), anyString(), anyInt()))
                .thenReturn(item(42, 1, "Drill", "", 15, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // blank name first, then real name
        new ListItemScreen(prompter("\n\n   \nDrill\n\n15\n", out), items).run(1);

        verify(items).createItem(1, "Drill", "", 15);
    }

    @Test
    void nonNumericCost_isRetriedUntilParsable() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(1, "Drill", "", 15))
                .thenReturn(item(42, 1, "Drill", "", 15, ItemStatus.available));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // non-numeric, then numeric
        new ListItemScreen(prompter("Drill\n\nabc\n15\n", out), items).run(1);

        verify(items).createItem(1, "Drill", "", 15);
    }

    @Test
    void businessRuleException_propagatesToCaller() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(anyInt(), anyString(), anyString(), anyInt()))
                .thenThrow(new BusinessRuleException("owner not found"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> new ListItemScreen(prompter("Drill\n\n15\n", out), items).run(1));
        assertEquals("owner not found", ex.getMessage());
    }

    @Test
    void illegalArgument_propagatesToCaller() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(anyInt(), anyString(), anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("cost must be positive"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ListItemScreen(prompter("Drill\n\n15\n", out), items).run(1));
        assertEquals("cost must be positive", ex.getMessage());
    }

    @Test
    void noConfirmationMessage_whenCreateItemThrows() {
        ItemService items = mock(ItemService.class);
        when(items.createItem(anyInt(), anyString(), anyString(), anyInt()))
                .thenThrow(new BusinessRuleException("boom"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(BusinessRuleException.class,
                () -> new ListItemScreen(prompter("Drill\n\n15\n", out), items).run(1));

        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Listed "));
=======
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
        exec("INSERT INTO users (username) VALUES ('liisa')");   // owner, id 1
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
        screen("Ladder\n6-step ladder\n5\n").run(1);

        assertTrue(output().contains("Listed Ladder at 5 per day."));
        assertEquals("1", itemCount());
        assertEquals("Ladder", query("SELECT item_name FROM listed_items"));
        assertEquals("6-step ladder", query("SELECT description FROM listed_items"));
        assertEquals("5", query("SELECT cost_per_day FROM listed_items"));
        assertEquals("available", query("SELECT status FROM listed_items"));
        assertEquals("1", query("SELECT owner_id FROM listed_items"));
    }

    @Test
    void aBlankNameIsAskedAgain() throws SQLException {
        screen("\n   \nLadder\nx\n5\n").run(1);

        assertTrue(output().contains("Please enter a value."));
        assertEquals("1", itemCount());
        assertEquals("Ladder", query("SELECT item_name FROM listed_items"));
    }

    @Test
    void aNonNumericCostIsAskedAgain() throws SQLException {
        screen("Ladder\nx\nabc\n1.5\n5\n").run(1);

        assertTrue(output().contains("Please enter a whole number."));
        assertEquals("5", query("SELECT cost_per_day FROM listed_items"));
    }

    @Test
    void aCostBelowOneIsRejectedByTheServiceAndNothingIsStored() throws SQLException {
        // the screen does not catch it, MainMenu reports it
        assertThrows(IllegalArgumentException.class, () -> screen("Ladder\nx\n0\n").run(1));

        assertEquals("0", itemCount());
    }

    @Test
    void endOfInputPartWayThroughStoresNothing() throws SQLException {
        assertThrows(InputClosedException.class, () -> screen("Ladder\n").run(1));
        assertThrows(InputClosedException.class, () -> screen("").run(1));

        assertEquals("0", itemCount());
>>>>>>> Stashed changes
    }
}