package com.rentaltracker.transport.screens;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.service.ItemDetail;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.transport.Prompter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryScreenTest {

    private Prompter prompter(String input, ByteArrayOutputStream out) {
        return new Prompter(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    /*
     * ItemDomain(int itemId, int ownerId, String itemName, String description,
     *            int costPerDay, ItemStatus status, LocalDateTime createdAt)
     */
    private ItemDomain item(int id, String name, ItemStatus status) {
        return new ItemDomain(
                id, 5, name, "Item desc", 10, status,
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    private ItemDetail detail(ItemDomain item, String owner) {
        return new ItemDetail(item, owner);
    }

    @Test
    void backFromList_exitsWithoutCallingDetail() {
        ItemService items = mock(ItemService.class);
        when(items.getItemsByOwner(1)).thenReturn(List.of(item(42, "Drill", ItemStatus.available)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("b\n", out), items).run(1);

        verify(items, never()).getItemDetail(anyInt());
        verify(items, never()).delist(anyInt());
        verify(items, never()).relist(anyInt());
    }

    @Test
    void emptyList_doesNotPromptOrFetchDetail() {
        ItemService items = mock(ItemService.class);
        when(items.getItemsByOwner(1)).thenReturn(List.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("", out), items).run(1);

        verify(items, never()).getItemDetail(anyInt());
    }

    @Test
    void delistFromDetail_callsDelistAndLoopsBack() {
        ItemService items = mock(ItemService.class);
        ItemDomain listed = item(42, "Drill", ItemStatus.rented);
        when(items.getItemsByOwner(1)).thenReturn(List.of(listed));
        when(items.getItemDetail(42)).thenReturn(detail(listed, "alice"));
        when(items.delist(42)).thenReturn(item(42, "Drill", ItemStatus.unlisted));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("1\n1\nb\n", out), items).run(1);

        verify(items).delist(42);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Drill is now unlisted"));
    }

    @Test
    void relistFromDetail_callsRelist() {
        ItemService items = mock(ItemService.class);
        ItemDomain unlisted = item(42, "Drill", ItemStatus.unlisted);
        when(items.getItemsByOwner(1)).thenReturn(List.of(unlisted));
        when(items.getItemDetail(42)).thenReturn(detail(unlisted, "alice"));
        when(items.relist(42)).thenReturn(item(42, "Drill", ItemStatus.rented));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("1\n1\nb\n", out), items).run(1);

        verify(items).relist(42);
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Drill is now listed"));
    }

    @Test
    void businessRuleException_isPrintedNotThrown() {
        ItemService items = mock(ItemService.class);
        ItemDomain listed = item(42, "Drill", ItemStatus.rented);
        when(items.getItemsByOwner(1)).thenReturn(List.of(listed));
        when(items.getItemDetail(42)).thenReturn(detail(listed, "alice"));
        when(items.delist(42)).thenThrow(new BusinessRuleException("cannot delist rented item"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("1\n1\nb\n", out), items).run(1);

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("cannot delist rented item"));
    }

    @Test
    void backFromDetail_doesNotChangeStatus() {
        ItemService items = mock(ItemService.class);
        ItemDomain listed = item(42, "Drill", ItemStatus.rented);
        when(items.getItemsByOwner(1)).thenReturn(List.of(listed));
        when(items.getItemDetail(42)).thenReturn(detail(listed, "alice"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("1\n2\nb\n", out), items).run(1);

        verify(items, never()).delist(anyInt());
        verify(items, never()).relist(anyInt());
    }

    @Test
    void detailView_showsFieldsAndCorrectActionLabel() {
        ItemService items = mock(ItemService.class);
        ItemDomain listed = item(42, "Drill", ItemStatus.rented);
        when(items.getItemsByOwner(1)).thenReturn(List.of(listed));
        when(items.getItemDetail(42)).thenReturn(detail(listed, "alice"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("1\n2\nb\n", out), items).run(1);

        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("=== Drill ==="));
        assertTrue(printed.contains("alice"));
        assertTrue(printed.contains("1) Delist"));
        assertTrue(printed.contains("2) Back to list"));
    }

    @Test
    void unlistedDetail_showsRelistLabel() {
        ItemService items = mock(ItemService.class);
        ItemDomain unlisted = item(42, "Drill", ItemStatus.unlisted);
        when(items.getItemsByOwner(1)).thenReturn(List.of(unlisted));
        when(items.getItemDetail(42)).thenReturn(detail(unlisted, "alice"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new InventoryScreen(prompter("1\n2\nb\n", out), items).run(1);

        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("1) Relist"));
        assertFalse(printed.contains("1) Delist"));
    }
}