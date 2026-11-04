package com.rentaltracker.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rentaltracker.repository.exception.DatabaseConnectionException;

class DataInitializerTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:?foreign_keys=on");
    }

    @AfterEach
    void tearDown() throws SQLException {
        conn.close();
    }

    private int count(String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void initializeCreatesTheThreeTables() throws SQLException {
        DataInitializer.initialize(conn);

        assertEquals(3, count("SELECT count(*) FROM sqlite_master "
                + "WHERE type = 'table' AND name IN ('users', 'listed_items', 'rentals')"));
    }

    @Test
    void initializeIsIdempotent() throws SQLException {
        DataInitializer.initialize(conn);
        assertDoesNotThrow(() -> DataInitializer.initialize(conn));

        assertEquals(3, count("SELECT count(*) FROM sqlite_master "
                + "WHERE type = 'table' AND name IN ('users', 'listed_items', 'rentals')"));
    }

    @Test
    void initializeOnClosedConnectionThrowsDatabaseConnectionException() throws SQLException {
        conn.close();
        assertThrows(DatabaseConnectionException.class, () -> DataInitializer.initialize(conn));
    }
}