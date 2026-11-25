package com.rentaltracker.transport;

import com.rentaltracker.repository.exception.RepositoryException;
import com.rentaltracker.service.Session;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.transport.exception.InputClosedException;
import com.rentaltracker.transport.screens.ConfirmReturnScreen;
import com.rentaltracker.transport.screens.InventoryScreen;
import com.rentaltracker.transport.screens.ListItemScreen;
import com.rentaltracker.transport.screens.RecordRentalScreen;
import com.rentaltracker.transport.screens.StartupScreen;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MainMenuTest {

    private Prompter prompter(String input, ByteArrayOutputStream out) {
        return new Prompter(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    /** Builds a MainMenu wired to mocks, with startup returning a user with the given id. */
    private MainMenu menu(String input, ByteArrayOutputStream out,
                          StartupScreen startup, ListItemScreen listItem,
                          InventoryScreen inventory, RecordRentalScreen recordRental,
                          ConfirmReturnScreen confirmReturn) {
        return new MainMenu(prompter(input, out), startup, listItem, inventory, recordRental, confirmReturn);
    }

    private static Session session(int userId) {
        return new Session(userId, "user" + userId);
    }

    /** A startup screen that "logs in" the user with this id. */
    private StartupScreen startupReturning(int userId) {
        StartupScreen startup = mock(StartupScreen.class);
        when(startup.run()).thenReturn(session(userId));
        return startup;
    }

    // ---------------------------------------------------------------------
    // Exit / greeting
    // ---------------------------------------------------------------------

    @Test
    void exitChoice_logsOutAndReturns() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StartupScreen startup = startupReturning(1);

        menu("5\n", out, startup, mock(ListItemScreen.class), mock(InventoryScreen.class),
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Logged out."));
        assertTrue(printed.contains("Goodbye."));
        verify(startup, times(1)).run();   // logged in once, before the menu
    }

    @Test
    void menuHeader_isPrintedBeforeFirstChoice() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        menu("5\n", out, startupReturning(1), mock(ListItemScreen.class), mock(InventoryScreen.class),
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("=== Rental tracker ==="));
        assertTrue(printed.contains("1) List an item"));
        assertTrue(printed.contains("2) View my inventory"));
        assertTrue(printed.contains("3) Record a rental"));
        assertTrue(printed.contains("4) Confirm a return"));
        assertTrue(printed.contains("5) Exit"));
    }

    // ---------------------------------------------------------------------
    // Routing: each choice hits the right screen with the owner id
    // ---------------------------------------------------------------------

    @Test
    void choice1_routesToListItemWithOwnerId() {
        ListItemScreen listItem = mock(ListItemScreen.class);
        StartupScreen startup = startupReturning(7);

        menu("1\n5\n", new ByteArrayOutputStream(), startup, listItem, mock(InventoryScreen.class),
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        verify(listItem).run(session(7));
    }

    @Test
    void choice2_routesToInventoryWithOwnerId() {
        InventoryScreen inventory = mock(InventoryScreen.class);
        StartupScreen startup = startupReturning(7);

        menu("2\n5\n", new ByteArrayOutputStream(), startup, mock(ListItemScreen.class), inventory,
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        verify(inventory).run(session(7));
    }

    @Test
    void choice3_routesToRecordRentalWithOwnerId() {
        RecordRentalScreen recordRental = mock(RecordRentalScreen.class);
        StartupScreen startup = startupReturning(7);

        menu("3\n5\n", new ByteArrayOutputStream(), startup, mock(ListItemScreen.class),
                mock(InventoryScreen.class), recordRental, mock(ConfirmReturnScreen.class)).run();

        verify(recordRental).run(session(7));
    }

    @Test
    void choice4_routesToConfirmReturnWithOwnerId() {
        ConfirmReturnScreen confirmReturn = mock(ConfirmReturnScreen.class);
        StartupScreen startup = startupReturning(7);

        menu("4\n5\n", new ByteArrayOutputStream(), startup, mock(ListItemScreen.class),
                mock(InventoryScreen.class), mock(RecordRentalScreen.class), confirmReturn).run();

        verify(confirmReturn).run(session(7));
    }

    @Test
    void multipleChoices_loopUntilExit() {
        ListItemScreen listItem = mock(ListItemScreen.class);
        InventoryScreen inventory = mock(InventoryScreen.class);
        StartupScreen startup = startupReturning(7);

        menu("1\n2\n5\n", new ByteArrayOutputStream(), startup, listItem, inventory,
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        verify(listItem).run(session(7));
        verify(inventory).run(session(7));
    }

    @Test
    void ownerId_fromStartup_isForwardedToEveryScreen() {
        ListItemScreen listItem = mock(ListItemScreen.class);
        InventoryScreen inventory = mock(InventoryScreen.class);
        RecordRentalScreen recordRental = mock(RecordRentalScreen.class);
        ConfirmReturnScreen confirmReturn = mock(ConfirmReturnScreen.class);
        StartupScreen startup = startupReturning(99);

        menu("1\n2\n3\n4\n5\n", new ByteArrayOutputStream(), startup, listItem, inventory,
                recordRental, confirmReturn).run();

        verify(listItem).run(session(99));
        verify(inventory).run(session(99));
        verify(recordRental).run(session(99));
        verify(confirmReturn).run(session(99));
    }

    // ---------------------------------------------------------------------
    // Per-screen error handling (runScreen catches)
    // ---------------------------------------------------------------------

    @Test
    void illegalArgumentFromScreen_isPrintedAndMenuContinues() {
        ListItemScreen listItem = mock(ListItemScreen.class);
        doThrow(new IllegalArgumentException("bad name")).when(listItem).run(any(Session.class));
        ListItemScreen ignored = listItem;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // route choice 1 (throws), then exit
        menu("1\n5\n", out, startupReturning(1), ignored, mock(InventoryScreen.class),
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Error: bad name"));
        assertTrue(printed.contains("Goodbye."));
    }

    @Test
    void businessRuleExceptionFromScreen_isPrintedAndMenuContinues() {
        InventoryScreen inventory = mock(InventoryScreen.class);
        doThrow(new BusinessRuleException("cannot delist")).when(inventory).run(any(Session.class));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        menu("2\n5\n", out, startupReturning(1), mock(ListItemScreen.class), inventory,
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Error: cannot delist"));
    }

    @Test
    void repositoryExceptionFromScreen_isPrintedAsDatabaseProblemAndMenuContinues() {
        RecordRentalScreen recordRental = mock(RecordRentalScreen.class);
        doThrow(new RepositoryException("disk full")).when(recordRental).run(any(Session.class));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        menu("3\n5\n", out, startupReturning(1), mock(ListItemScreen.class),
                mock(InventoryScreen.class), recordRental, mock(ConfirmReturnScreen.class)).run();

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Database problem: disk full"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Goodbye."));
    }

    @Test
    void errorOnOneScreen_doesNotPreventNextScreenFromRunning() {
        ListItemScreen listItem = mock(ListItemScreen.class);
        InventoryScreen inventory = mock(InventoryScreen.class);
        doThrow(new BusinessRuleException("boom")).when(listItem).run(any(Session.class));

        menu("1\n2\n5\n", new ByteArrayOutputStream(), startupReturning(1), listItem, inventory,
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        verify(inventory).run(session(1));
    }

    // ---------------------------------------------------------------------
    // Top-level error handling (run catches)
    // ---------------------------------------------------------------------

    @Test
    void repositoryExceptionFromStartup_isPrintedAndMenuDoesNotLoop() {
        StartupScreen startup = mock(StartupScreen.class);
        when(startup.run()).thenThrow(new RepositoryException("db down"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        menu("", out, startup, mock(ListItemScreen.class), mock(InventoryScreen.class),
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Database problem: db down"));
        // no menu header since startup failed before the loop
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Rental tracker"));
    }

    @Test
    void repositoryExceptionFromStartup_doesNotCallAnyScreen() {
        StartupScreen startup = mock(StartupScreen.class);
        when(startup.run()).thenThrow(new RepositoryException("db down"));
        ListItemScreen listItem = mock(ListItemScreen.class);
        InventoryScreen inventory = mock(InventoryScreen.class);
        RecordRentalScreen recordRental = mock(RecordRentalScreen.class);
        ConfirmReturnScreen confirmReturn = mock(ConfirmReturnScreen.class);

        menu("", new ByteArrayOutputStream(), startup, listItem, inventory, recordRental, confirmReturn).run();

        verifyNoInteractions(listItem, inventory, recordRental, confirmReturn);
    }

    @Test
    void inputClosedException_isTreatedAsCleanExit() {
        StartupScreen startup = mock(StartupScreen.class);
        when(startup.run()).thenThrow(new InputClosedException());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        menu("", out, startup, mock(ListItemScreen.class), mock(InventoryScreen.class),
                mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run();

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Goodbye."));
    }

    @Test
    void inputClosedAfterOneChoice_printsGoodbyeWithoutCrashing() {
        ListItemScreen listItem = mock(ListItemScreen.class);
        StartupScreen startup = startupReturning(1);

        // one valid choice, then EOF — askChoice should raise InputClosedException
        assertDoesNotThrow(() ->
                menu("1\n", new ByteArrayOutputStream(), startup, listItem, mock(InventoryScreen.class),
                        mock(RecordRentalScreen.class), mock(ConfirmReturnScreen.class)).run());

        verify(listItem).run(session(1));
    }
}