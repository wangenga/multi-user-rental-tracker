package com.rentaltracker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.RentalDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.domain.enums.RentalStatus;
import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.CapturingLogger;
import com.rentaltracker.logging.LogEvents;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.SqliteTransactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.repository.exception.ConstraintViolationException;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.service.exception.NotOwnerException;

class ItemServiceTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 6, 10, 9, 0);
    static final LocalDateTime END = LocalDateTime.of(2026, 6, 13, 9, 0);

    @TempDir Path tempDir;
    Database db;
    RentalRepository rentals;
    ItemService service;
    CapturingLogger logger = new CapturingLogger();

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        rentals = new RentalRepository(db);
        service = new ItemService(new ItemRepository(db.connection()), rentals,
                new UserService(new UserRepository(db)), new SqliteTransactor(db), logger);
        // 1 = owner, 2 = renter, 3 = a second owner
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x'), ('marili', 'x')");
    }

    @AfterEach
    void tearDown() { db.close(); }

    // ---------- helpers that talk to the database directly ----------

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

    private String itemStatus(long id) throws SQLException {
        return query("SELECT status FROM listed_items WHERE item_id = " + id);
    }

    private String itemCount() throws SQLException {
        return query("SELECT count(*) FROM listed_items");
    }

    private long addItem(int ownerId, String name, ItemStatus status) throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
           + "VALUES (" + ownerId + ", '" + name + "', 'x', 5, '" + status.name() + "')");
        return Long.parseLong(query("SELECT last_insert_rowid()"));
    }

    private long addActiveRental(long itemId) {
        return rentals.insert(new RentalDomain(0, itemId, 2, START, END, null, RentalStatus.ACTIVE));
    }

    // ---------- createItem ----------

    @Test
    void createItemListsItAsAvailableAndTrimsTheText() throws SQLException {
        ItemDomain item = service.createItem(1, "  Ladder ", " 6-step ", 5);

        long id = item.getItemId();
        assertEquals("available", itemStatus(id));
        assertEquals("Ladder", query("SELECT item_name FROM listed_items WHERE item_id = " + id));
        assertEquals("6-step", query("SELECT description FROM listed_items WHERE item_id = " + id));
        assertEquals("5", query("SELECT cost_per_day FROM listed_items WHERE item_id = " + id));
    }

    @Test
    void aMissingDescriptionIsStoredAsEmptyText() throws SQLException {
        ItemDomain item = service.createItem(1, "Ladder", null, 5);

        assertEquals("", query("SELECT description FROM listed_items WHERE item_id = " + item.getItemId()));
    }

    @Test
    void aNameOfExactlyOneHundredCharactersIsAccepted() {
        assertEquals(100, service.createItem(1, "a".repeat(100), "x", 5).getItemName().length());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void createItemRejectsABlankNameAndStoresNothing(String name) throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.createItem(1, name, "x", 5));

        assertEquals("0", itemCount());
    }

    @Test
    void createItemRejectsANameOverOneHundredCharacters() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.createItem(1, "a".repeat(101), "x", 5));

        assertEquals("0", itemCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void createItemRejectsACostBelowOneAndStoresNothing(int cost) throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.createItem(1, "Ladder", "x", cost));

        assertEquals("0", itemCount());
    }

    @Test
    void aCostOfOneIsAccepted() {
        assertEquals(1, service.createItem(1, "Ladder", "x", 1).getCostPerDay());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void createItemRejectsANonPositiveOwnerId(int ownerId) throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> service.createItem(ownerId, "Ladder", "x", 5));

        assertEquals("0", itemCount());
    }

    @Test
    void createItemForAnUnknownOwnerViolatesTheForeignKey() throws SQLException {
        assertThrows(ConstraintViolationException.class, () -> service.createItem(99, "Ladder", "x", 5));

        assertEquals("0", itemCount());
    }

    // ---------- reads ----------

    @Test
    void getItemByIdFindsAnExistingItem() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        assertEquals("Ladder", service.getItemById((int) id).orElseThrow().getItemName());
    }

    @Test
    void getItemByIdIsEmptyForAnUnknownId() {
        assertTrue(service.getItemById(999).isEmpty());
    }

    @Test
    void getItemByIdRejectsANonPositiveId() {
        assertThrows(IllegalArgumentException.class, () -> service.getItemById(0));
    }

    @Test
    void requireItemByIdThrowsNotFoundForAnUnknownId() {
        assertThrows(NotFoundException.class, () -> service.requireItemById(999));
    }

    @Test
    void getItemsByOwnerReturnsOnlyThatOwnersItemsInIdOrder() throws SQLException {
        addItem(1, "Ladder", ItemStatus.available);
        addItem(3, "Saw", ItemStatus.available);
        addItem(1, "Drill", ItemStatus.unlisted);

        List<String> names = service.getItemsByOwner(1).stream().map(ItemDomain::getItemName).toList();

        assertEquals(List.of("Ladder", "Drill"), names);
    }

    @Test
    void getItemsByOwnerRejectsANonPositiveId() {
        assertThrows(IllegalArgumentException.class, () -> service.getItemsByOwner(0));
    }

    @Test
    void getItemDetailIncludesTheOwnersUsername() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        ItemDetail detail = service.getItemDetail((int) id);

        assertEquals("Ladder", detail.item().getItemName());
        assertEquals("liisa", detail.ownerUsername());
    }

    @Test
    void getItemDetailOnAMissingItemThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> service.getItemDetail(999));
    }

    // ---------- delist ----------

    @Test
    void delistMakesAnAvailableItemUnlisted() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        service.delist(1, (int) id);

        assertEquals("unlisted", itemStatus(id));
    }

    @Test
    void delistingARentedItemLeavesItsRentalActive() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.rented);
        long rentalId = addActiveRental(id);

        service.delist(1, (int) id);

        assertEquals("unlisted", itemStatus(id));
        assertEquals("active", query("SELECT status FROM rentals WHERE rental_id = " + rentalId));
    }

    @Test
    void delistingAnUnlistedItemIsRejected() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.unlisted);

        assertThrows(BusinessRuleException.class, () -> service.delist(1, (int) id));

        assertEquals("unlisted", itemStatus(id));
    }

    @Test
    void delistingAMissingItemThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> service.delist(1, 999));
    }

    // ---------- relist ----------

    @Test
    void relistMakesAnUnlistedItemAvailable() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.unlisted);

        service.relist(1, (int) id);

        assertEquals("available", itemStatus(id));
    }

    @Test
    void relistIsRefusedWhileTheItemIsStillOutOnARental() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.unlisted);
        addActiveRental(id);

        assertThrows(BusinessRuleException.class, () -> service.relist(1, (int) id));

        assertEquals("unlisted", itemStatus(id));
    }

    @Test
    void relistWorksOnceTheRentalHasBeenReturned() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.unlisted);
        long rentalId = addActiveRental(id);
        rentals.close(rentalId, LocalDateTime.of(2026, 6, 12, 9, 0));

        service.relist(1, (int) id);

        assertEquals("available", itemStatus(id));
    }

    @Test
    void relistingAnAvailableItemIsRejected() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        assertThrows(BusinessRuleException.class, () -> service.relist(1, (int) id));
    }

    // ---------- ownership: only the owner may delist or relist ----------

    @Test
    void aUserCannotDelistSomeoneElsesItemAndNothingChanges() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        NotOwnerException e = assertThrows(NotOwnerException.class, () -> service.delist(2, (int) id));

        assertEquals("You can only delist your own items.", e.getMessage());
        assertEquals("available", itemStatus(id));
    }

    @Test
    void aUserCannotRelistSomeoneElsesItemAndNothingChanges() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.unlisted);

        NotOwnerException e = assertThrows(NotOwnerException.class, () -> service.relist(3, (int) id));

        assertEquals("You can only relist your own items.", e.getMessage());
        assertEquals("unlisted", itemStatus(id));
    }

    @Test
    void ownershipIsCheckedBeforeTheItemsStateIsRevealed() throws SQLException {
        // a stranger relisting an item that is still out must hear "not yours", not "still out on a rental"
        long id = addItem(1, "Ladder", ItemStatus.unlisted);
        addActiveRental(id);

        assertThrows(NotOwnerException.class, () -> service.relist(2, (int) id));
        // and delisting an already-unlisted item: "not yours", not "cannot change from unlisted"
        assertThrows(NotOwnerException.class, () -> service.delist(2, (int) id));
    }

    @Test
    void aNotOwnerRefusalIsABusinessRuleSoEveryExistingCatchHandlesIt() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        assertThrows(BusinessRuleException.class, () -> service.delist(2, (int) id));
    }

    @Test
    void aRefusedAttemptIsLoggedAtWarnWithWhoTriedWhatOnWhichItem() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        assertThrows(NotOwnerException.class, () -> service.delist(2, (int) id));

        assertTrue(logger.has(AppLogger.Level.WARN, LogEvents.OWNERSHIP_DENIED));
        var fields = logger.entries().get(0).fields();
        assertEquals("delist", fields.get("action"));
        assertEquals("2", fields.get("actor_id"));
        assertEquals(String.valueOf(id), fields.get("item_id"));
    }

    @Test
    void theOwnerIsNotLoggedAsDenied() throws SQLException {
        long id = addItem(1, "Ladder", ItemStatus.available);

        service.delist(1, (int) id);

        assertTrue(logger.entries().isEmpty());
    }
}