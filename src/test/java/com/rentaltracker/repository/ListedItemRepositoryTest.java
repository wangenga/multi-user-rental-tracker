package com.rentaltracker.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.repository.exception.MappingException;
import com.rentaltracker.repository.exception.RepositoryException;

class ListedItemRepositoryTest {

    @TempDir Path tempDir;
    Database db;
    ItemRepository items;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tempDir.resolve("test.db").toString());
        items = new ItemRepository(db.connection());

        // users: 1 and 2 (both potential owners)
        exec("INSERT INTO users (username, password) VALUES ('liisa', 'x'), ('meelis', 'x')");
    }

    @AfterEach
    void tearDown() { db.close(); }

    // ---- helpers that talk to the database directly ----

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

    private ItemDomain newItem(int ownerId, String name, String desc, int costPerDay) {
        return new ItemDomain(0, ownerId, name, desc, costPerDay, ItemStatus.available, null);
    }

    private ItemDomain saveItem(int ownerId, String name, String desc, int costPerDay)
            throws SQLException {
        ItemDomain item = newItem(ownerId, name, desc, costPerDay);
        items.save(item);
        return item;
    }

    // ---- save ----

    @Test
    void saveStoresTheRowAndSetsTheId() throws SQLException {
        ItemDomain item = saveItem(1, "Ladder", "Tall", 5);

        assertTrue(item.getItemId() > 0);
        assertEquals("1", query("SELECT count(*) FROM listed_items"));
        assertEquals("1",     query("SELECT owner_id FROM listed_items WHERE item_id = " + item.getItemId()));
        assertEquals("Ladder", query("SELECT item_name FROM listed_items WHERE item_id = " + item.getItemId()));
        assertEquals("Tall",   query("SELECT description FROM listed_items WHERE item_id = " + item.getItemId()));
        assertEquals("5",     query("SELECT cost_per_day FROM listed_items WHERE item_id = " + item.getItemId()));
    }

    @Test
    void saveSetsCreatedAtOnTheDomainObject() throws SQLException {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ItemDomain item = saveItem(1, "Drill", "Powerful", 4);

        assertNotNull(item.getCreatedAt());
        assertTrue(item.getCreatedAt().isAfter(before));
        assertNotNull(query("SELECT created_at FROM listed_items WHERE item_id = " + item.getItemId()));
    }

    @Test
    void saveAssignsUniqueIdsToEachItem() throws SQLException {
        ItemDomain a = saveItem(1, "A", "x", 1);
        ItemDomain b = saveItem(1, "B", "x", 1);

        assertNotEquals(a.getItemId(), b.getItemId());
    }


    // ---- findByItemId ----

    @Test
    void findByItemIdReturnsTheStoredItem() throws SQLException {
        ItemDomain item = saveItem(1, "Drill", "Powerful", 4);

        Optional<ItemDomain> found = items.findByItemId(item.getItemId());

        assertTrue(found.isPresent());
        ItemDomain loaded = found.get();
        assertEquals(item.getItemId(), loaded.getItemId());
        assertEquals(1, loaded.getOwnerId());
        assertEquals("Drill", loaded.getItemName());
        assertEquals("Powerful", loaded.getDescription());
        assertEquals(4, loaded.getCostPerDay());
        assertEquals(ItemStatus.available, loaded.getStatus());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    void findByItemIdIsEmptyForMissingItem() throws SQLException {
        assertTrue(items.findByItemId(999).isEmpty());
    }

    // ---- findAll ----

    @Test
    void findAllReturnsEveryStoredItem() throws SQLException {
        saveItem(1, "A", "x", 1);
        saveItem(1, "B", "x", 2);
        saveItem(2, "C", "x", 3);

        List<ItemDomain> all = items.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void findAllIsEmptyWhenNothingIsStored() throws SQLException {
        assertTrue(items.findAll().isEmpty());
    }

    // ---- update ----

    @Test
    void updateChangesAllMutableFields() throws SQLException {
        ItemDomain item = saveItem(1, "Old", "old desc", 5);

        item.setItemName("New");
        item.setDescription("new desc");
        item.setCostPerDay(9);
        item.setOwnerId(2);
        items.update(item);

        assertEquals("New",      query("SELECT item_name FROM listed_items WHERE item_id = " + item.getItemId()));
        assertEquals("new desc", query("SELECT description FROM listed_items WHERE item_id = " + item.getItemId()));
        assertEquals("9",       query("SELECT cost_per_day FROM listed_items WHERE item_id = " + item.getItemId()));
        assertEquals("2",       query("SELECT owner_id FROM listed_items WHERE item_id = " + item.getItemId()));
    }

    @Test
    void updateChangesStatus() throws SQLException {
        ItemDomain item = saveItem(1, "Drill", "x", 4);

        item.setStatus(ItemStatus.rented);
        items.update(item);

        assertEquals(ItemStatus.rented,
                items.findByItemId(item.getItemId()).orElseThrow().getStatus());
    }

    @Test
    void updateOnMissingIdIsANoOp() throws SQLException {
        ItemDomain ghost = newItem(1, "Ghost", "x", 1); // itemId == 0

        items.update(ghost);

        assertEquals("0", query("SELECT count(*) FROM listed_items"));
    }

    // ---- deleteById ----

    @Test
    void deleteByIdRemovesTheRow() throws SQLException {
        ItemDomain item = saveItem(1, "Doomed", "x", 1);

        items.deleteById(item.getItemId());

        assertEquals("0", query("SELECT count(*) FROM listed_items"));
        assertTrue(items.findByItemId(item.getItemId()).isEmpty());
    }

    @Test
    void deleteByIdLeavesOtherRowsAlone() throws SQLException {
        ItemDomain kept   = saveItem(1, "Kept",   "x", 1);
        ItemDomain doomed = saveItem(1, "Doomed", "x", 1);

        items.deleteById(doomed.getItemId());

        assertEquals("1", query("SELECT count(*) FROM listed_items"));
        assertTrue(items.findByItemId(kept.getItemId()).isPresent());
    }

    @Test
    void deleteByIdOnMissingIdIsANoOp() throws SQLException {
        saveItem(1, "Kept", "x", 1);

        items.deleteById(999);

        assertEquals("1", query("SELECT count(*) FROM listed_items"));
    }

    // ---- round-trip sanity ----

    @Test
    void savedItemCanBeReadBackThroughFindByItemId() throws SQLException {
        ItemDomain item = saveItem(1, "RoundTrip", "desc", 7);

        ItemDomain loaded = items.findByItemId(item.getItemId()).orElseThrow();

        assertEquals(item.getItemName(),  loaded.getItemName());
        assertEquals(item.getOwnerId(),   loaded.getOwnerId());
        assertEquals(item.getCostPerDay(), loaded.getCostPerDay());
    }

    @Test
    void findingARowWithAnUnreadableTimestampThrowsMappingException() throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, created_at) "
                + "VALUES (1, 'Ladder', 'x', 5, 'garbage')");

        assertThrows(MappingException.class, () -> items.findByItemId(1));
    }

    // =====================================================================
    // save: status column, created_at format, FK, catch block, re-save
    // =====================================================================

    @Test
    void saveWritesStatusColumn() throws SQLException {
        saveItem(1, "Drill", "x", 4);

        assertEquals("available",
                query("SELECT status FROM listed_items WHERE item_name = 'Drill'"));
    }

    @Test
    void saveWritesCreatedAtThatParsesBackOnFindByItemId() throws SQLException {
        ItemDomain item = saveItem(1, "Drill", "x", 4);

        ItemDomain loaded = items.findByItemId(item.getItemId()).orElseThrow();

        // DbTime stores minute precision; compare the parts that survive the round-trip.
        LocalDateTime expected = item.getCreatedAt().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime actual   = loaded.getCreatedAt().truncatedTo(ChronoUnit.MINUTES);
        assertEquals(expected, actual);
    }

    @Test
    void saveForMissingOwnerViolatesForeignKey() {
        ItemDomain orphan = newItem(99, "Orphan", "x", 1);

        assertThrows(RepositoryException.class, () -> items.save(orphan));
    }

    @Test
    void saveOnClosedConnectionThrowsRepositoryException() {
        ItemDomain item = newItem(1, "X", "x", 1);
        db.close();

        assertThrows(RepositoryException.class, () -> items.save(item));
    }

    @Test
    void savingSameObjectTwiceInsertsTwoRowsAndOverwritesId() throws SQLException {
        ItemDomain item = saveItem(1, "Twice", "x", 1);
        int firstId = item.getItemId();

        items.save(item);

        assertEquals("2", query("SELECT count(*) FROM listed_items"));
        // save() writes the newly generated key back onto the object.
        assertNotEquals(firstId, item.getItemId());
    }

    // =====================================================================
    // findByItemId: status round-trip, catch block
    // =====================================================================

    @Test
    void findByItemIdRoundTripsEveryStatus() throws SQLException {
        for (ItemStatus status : ItemStatus.values()) {
            ItemDomain item = newItem(1, "S-" + status, "x", 1);
            item.setStatus(status);
            items.save(item);

            assertEquals(status,
                    items.findByItemId(item.getItemId()).orElseThrow().getStatus());
        }
    }

    @Test
    void findByItemIdOnClosedConnectionThrowsRepositoryException() {
        db.close();

        assertThrows(RepositoryException.class, () -> items.findByItemId(1));
    }

    // =====================================================================
    // findAll: content, mapping, catch block, unreadable row
    // =====================================================================

    @Test
    void findAllReturnsItemsWithTheirFields() throws SQLException {
        saveItem(1, "Alpha", "first",  1);
        saveItem(2, "Beta",  "second", 2);

        List<ItemDomain> all = items.findAll();

        List<String> names = all.stream().map(ItemDomain::getItemName).toList();
        assertTrue(names.containsAll(List.of("Alpha", "Beta")));
        for (ItemDomain it : all) {
            assertNotNull(it.getCreatedAt());
            assertNotNull(it.getStatus());
        }
    }

    @Test
    void findAllMapsEachRowIndependently() throws SQLException {
        ItemDomain a = saveItem(1, "A", "aa", 1);
        ItemDomain b = saveItem(1, "B", "bb", 2);

        List<ItemDomain> all = items.findAll();
        ItemDomain loadedA = all.stream()
                .filter(x -> x.getItemName().equals("A")).findFirst().orElseThrow();
        ItemDomain loadedB = all.stream()
                .filter(x -> x.getItemName().equals("B")).findFirst().orElseThrow();

        assertEquals(a.getItemId(), loadedA.getItemId());
        assertEquals("aa", loadedA.getDescription());
        assertEquals(1, loadedA.getCostPerDay());

        assertEquals(b.getItemId(), loadedB.getItemId());
        assertEquals("bb", loadedB.getDescription());
        assertEquals(2, loadedB.getCostPerDay());
    }

    @Test
    void findAllOnClosedConnectionThrowsRepositoryException() {
        db.close();

        assertThrows(RepositoryException.class, () -> items.findAll());
    }

    @Test
    void findAllWithUnreadableRowThrowsMappingException() throws SQLException {
        exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, created_at) "
                + "VALUES (1, 'Bad', 'x', 1, 'garbage')");

        assertThrows(MappingException.class, () -> items.findAll());
    }

    @Test
    void unknownStatusStringThrowsMappingException() throws SQLException {
        exec("PRAGMA ignore_check_constraints = ON");
        try {
            exec("INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status) "
                    + "VALUES (1, 'Weird', 'x', 1, 'not-a-status')");
        } finally {
            exec("PRAGMA ignore_check_constraints = OFF");
        }

        assertThrows(MappingException.class, () -> items.findByItemId(1));
    }

    // =====================================================================
    // update: created_at untouched, FK, catch block
    // =====================================================================

    @Test
    void updateDoesNotChangeCreatedAt() throws SQLException {
        ItemDomain item = saveItem(1, "Drill", "x", 4);
        String before = query("SELECT created_at FROM listed_items WHERE item_id = " + item.getItemId());

        item.setItemName("Drill2");
        items.update(item);

        String after = query("SELECT created_at FROM listed_items WHERE item_id = " + item.getItemId());
        assertEquals(before, after);
    }

    @Test
    void updateToMissingOwnerViolatesForeignKey() throws SQLException {
        ItemDomain item = saveItem(1, "Drill", "x", 4);
        item.setOwnerId(99);

        assertThrows(RepositoryException.class, () -> items.update(item));
    }

    @Test
    void updateOnClosedConnectionThrowsRepositoryException() {
        ItemDomain item = newItem(1, "X", "x", 1);
        item.setItemId(1);
        db.close();

        assertThrows(RepositoryException.class, () -> items.update(item));
    }

    // =====================================================================
    // deleteById: FK (rental references item), catch block
    // =====================================================================

    @Test
    void deleteByIdWithExistingRentalViolatesForeignKey() throws SQLException {
        ItemDomain item = saveItem(1, "Drill", "x", 4);
        // Insert the rental *after* the item exists, so the FK on rentals is satisfied.
        exec("INSERT INTO rentals (item_id, renter_id, start_time, end_time) "
                + "VALUES (" + item.getItemId() + ", 1, '2026-06-16 10:00', '2026-06-19 10:00')");

        assertThrows(RepositoryException.class, () -> items.deleteById(item.getItemId()));
    }

    @Test
    void deleteByIdOnClosedConnectionThrowsRepositoryException() {
        db.close();

        assertThrows(RepositoryException.class, () -> items.deleteById(1));
    }
}