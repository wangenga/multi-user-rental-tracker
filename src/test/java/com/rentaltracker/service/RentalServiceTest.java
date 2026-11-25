package com.rentaltracker.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.RentalDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.domain.enums.RentalStatus;
import com.rentaltracker.domain.view.ListingView;
import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.SqliteTransactor;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.repository.exception.RepositoryException;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.service.exception.ValidationException;

class RentalServiceTest {

    // "Now" for every test: 16 June 2026, 14:30:45 (seconds get truncated by the service)
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-16T14:30:45Z"), ZoneId.of("UTC"));
    static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 16, 14, 30);

    static final LocalDateTime START = LocalDateTime.of(2026, 6, 10, 9, 0);
    static final LocalDateTime END = LocalDateTime.of(2026, 6, 13, 9, 0);

    @TempDir Path tempDir;
    Database db;
    ItemRepository items;
    RentalRepository rentals;
    RentalService service;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        items = new ItemRepository(db.connection());
        rentals = new RentalRepository(db);
        service = new RentalService(items, rentals, new SqliteTransactor(db), CLOCK);

        // user 1 = owner, 2 = renter, 3 = second owner
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

    private String itemStatus(long itemId) throws SQLException {
        return query("SELECT status FROM listed_items WHERE item_id = " + itemId);
    }

    private String rentalStatus(long rentalId) throws SQLException {
        return query("SELECT status FROM rentals WHERE rental_id = " + rentalId);
    }

    private String returnedAt(long rentalId) throws SQLException {
        return query("SELECT returned_at FROM rentals WHERE rental_id = " + rentalId);
    }

    private String rentalCount() throws SQLException {
        return query("SELECT count(*) FROM rentals");
    }

    /** Inserts an item with the given status and returns its id. */
    private long addItem(int ownerId, String name, ItemStatus status) throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
           + "VALUES (" + ownerId + ", '" + name + "', 'x', 5, '" + status.name() + "')");
        return Long.parseLong(query("SELECT last_insert_rowid()"));
    }

    private long addActiveRental(long itemId, LocalDateTime end) {
        return rentals.insert(new RentalDomain(0, itemId, 2, START, end, null, RentalStatus.ACTIVE));
    }

    // ---------- confirmReturn ----------

    @Test
    void confirmReturnClosesTheRentalAndFreesTheItem() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.rented);
        long rentalId = addActiveRental(itemId, END);

        ItemDomain result = service.confirmReturn(rentalId);

        assertEquals("closed", rentalStatus(rentalId));
        assertEquals("2026-06-16 14:30", returnedAt(rentalId));
        assertEquals("available", itemStatus(itemId));
        assertEquals(ItemStatus.available, result.getStatus());
    }

    @Test
    void confirmReturnLeavesAnItemUnlistedIfItWasDelistedWhileOut() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.unlisted);
        long rentalId = addActiveRental(itemId, END);

        service.confirmReturn(rentalId);

        assertEquals("closed", rentalStatus(rentalId));
        assertEquals("unlisted", itemStatus(itemId));
    }

    @Test
    void confirmReturnKeepsTheRentalRecord() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.rented);
        long rentalId = addActiveRental(itemId, END);

        service.confirmReturn(rentalId);

        assertEquals("1", rentalCount());
    }

    @Test
    void confirmReturnTwiceIsRejectedAndChangesNothing() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.rented);
        long rentalId = addActiveRental(itemId, END);
        service.confirmReturn(rentalId);

        assertThrows(BusinessRuleException.class, () -> service.confirmReturn(rentalId));

        assertEquals("closed", rentalStatus(rentalId));
        assertEquals("available", itemStatus(itemId));
    }

    @Test
    void confirmReturnOnAMissingRentalThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> service.confirmReturn(999));
    }

    @Test
    void confirmReturnRollsBackTheRentalIfTheItemUpdateFails() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.rented);
        long rentalId = addActiveRental(itemId, END);
        // make every item update fail, after the rental has already been closed
        exec("CREATE TRIGGER block_item_updates BEFORE UPDATE ON listed_items "
           + "BEGIN SELECT RAISE(ABORT, 'blocked'); END");

        assertThrows(RepositoryException.class, () -> service.confirmReturn(rentalId));

        assertEquals("active", rentalStatus(rentalId));
        assertNull(returnedAt(rentalId));
        assertEquals("rented", itemStatus(itemId));
    }

    // ---------- getActiveRentals ----------

    @Test
    void getActiveRentalsSortsByDueDateAndSkipsClosedAndOtherOwners() throws SQLException {
        long a = addItem(1, "Ladder", ItemStatus.rented);
        long b = addItem(1, "Drill", ItemStatus.rented);
        long c = addItem(1, "Tent", ItemStatus.available);
        long other = addItem(3, "Saw", ItemStatus.rented);

        long later = addActiveRental(a, LocalDateTime.of(2026, 6, 25, 10, 0));
        long sooner = addActiveRental(b, LocalDateTime.of(2026, 6, 18, 10, 0));
        long closed = addActiveRental(c, END);
        rentals.close(closed, LocalDateTime.of(2026, 6, 12, 9, 0));
        addActiveRental(other, END);

        List<Long> ids = service.getActiveRentals(1).stream().map(RentalDomain::id).toList();

        assertEquals(List.of(sooner, later), ids);
    }

    @Test
    void getActiveRentalsIsEmptyWhenNothingIsOut() {
        assertTrue(service.getActiveRentals(1).isEmpty());
    }

    // ---------- getListings ----------

    @Test
    void getListingsShowsOnlyOtherUsersAvailableItemsWithTheirOwner() throws SQLException {
        addItem(1, "Ladder", ItemStatus.available);    // the viewer's own
        addItem(3, "Saw", ItemStatus.available);
        addItem(3, "Drill", ItemStatus.rented);
        addItem(3, "Tent", ItemStatus.unlisted);

        List<ListingView> listings = service.getListings(1);

        assertEquals(1, listings.size());
        assertEquals("Saw", listings.get(0).itemName());
        assertEquals("marili", listings.get(0).ownerUsername());
    }

    @Test
    void getListingsIsEmptyWhenOnlyTheViewerHasItems() throws SQLException {
        addItem(1, "Ladder", ItemStatus.available);

        assertTrue(service.getListings(1).isEmpty());
    }

    // ---------- rent: success ----------

    @Test
    void rentMarksTheItemRentedAndRecordsTheLoggedInUserAsRenter() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.available);

        RentalDomain rental = service.rent(2, (int) itemId, 3);

        assertEquals("rented", itemStatus(itemId));
        assertEquals("1", rentalCount());
        assertEquals("active", rentalStatus(rental.id()));
        assertEquals("2", query("SELECT renter_id FROM rentals WHERE rental_id = " + rental.id()));
        assertEquals(itemId, rental.itemId());
        assertEquals(RentalStatus.ACTIVE, rental.status());
    }

    @Test
    void rentSetsTheEndTimeToStartPlusTheDaysEntered() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.available);

        RentalDomain rental = service.rent(2, (int) itemId, 3);

        // the fixed clock reads 14:30:45, which is stored truncated to the minute
        assertEquals(NOW, rental.startTime());
        assertEquals(NOW.plusDays(3), rental.endTime());
        assertEquals("2026-06-16 14:30",
                query("SELECT start_time FROM rentals WHERE rental_id = " + rental.id()));
        assertEquals("2026-06-19 14:30",
                query("SELECT end_time FROM rentals WHERE rental_id = " + rental.id()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 365})
    void rentAcceptsTheShortestAndLongestDurations(int days) throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.available);

        service.rent(2, (int) itemId, days);

        assertEquals("1", rentalCount());
    }

    // ---------- rent: rejections ----------

    @Test
    void rentingYourOwnItemIsRefusedByTheServiceAndChangesNothing() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.available);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.rent(1, (int) itemId, 3));

        assertTrue(e.getMessage().contains("your own item"), e.getMessage());
        assertEquals("0", rentalCount());
        assertEquals("available", itemStatus(itemId));
    }

    @Test
    void rentOnARentedItemIsRejectedAndChangesNothing() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.rented);

        assertThrows(BusinessRuleException.class, () -> service.rent(2, (int) itemId, 3));

        assertEquals("0", rentalCount());
        assertEquals("rented", itemStatus(itemId));
    }

    @Test
    void rentOnAnUnlistedItemIsRejectedAndChangesNothing() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.unlisted);

        assertThrows(BusinessRuleException.class, () -> service.rent(2, (int) itemId, 3));

        assertEquals("0", rentalCount());
        assertEquals("unlisted", itemStatus(itemId));
    }

    @Test
    void rentOnAMissingItemThrowsNotFound() throws SQLException {
        assertThrows(NotFoundException.class, () -> service.rent(2, 999, 3));

        assertEquals("0", rentalCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 366})
    void rentRejectsAnInvalidNumberOfDaysBeforeTouchingTheDatabase(int days) throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.available);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.rent(2, (int) itemId, days));

        assertEquals("Rental", e.getEntity());
        assertEquals("days", e.getField());
        assertEquals("0", rentalCount());
        assertEquals("available", itemStatus(itemId));
    }

    @Test
    void rentLeavesNoTraceWhenTheItemUpdateFailsAfterTheRentalIsInserted() throws SQLException {
        long itemId = addItem(1, "Ladder", ItemStatus.available);
        // the rental is inserted first, then this makes the item update fail
        exec("CREATE TRIGGER block_item_updates BEFORE UPDATE ON listed_items "
           + "BEGIN SELECT RAISE(ABORT, 'blocked'); END");

        assertThrows(RepositoryException.class, () -> service.rent(2, (int) itemId, 3));

        assertEquals("0", rentalCount());          // the rental insert was undone
        assertEquals("available", itemStatus(itemId));
    }
}
