package com.rentaltracker.transport.screens;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.transport.Prompter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
    }
}