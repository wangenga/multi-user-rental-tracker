package com.rentaltracker.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.rentaltracker.domain.ItemDomain;
import com.rentaltracker.domain.enums.ItemStatus;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.exception.NotFoundException;
import com.rentaltracker.service.exception.BusinessRuleException;
import com.rentaltracker.service.exception.ValidationException;
import com.rentaltracker.service.validation.ItemValidator;

public class ItemService {

    private final ItemRepository itemRepository;
    private final RentalRepository rentals;
    private final UserService users;
    private final Transactor tx;

    public ItemService(ItemRepository itemRepository, RentalRepository rentals,
                       UserService users, Transactor tx) {
        this.itemRepository = itemRepository;
        this.rentals = rentals;
        this.users = users;
        this.tx = tx;
    }

    // ---------- create ----------

    /**
     * Listing an item makes it available.
     * Every field is checked by ItemValidator first, so a rejected input never reaches the database.
     *
     * @throws ValidationException if any field breaks a rule (blank name, negative cost, ...)
     */
    public ItemDomain createItem(int ownerId, String itemName, String description, int costPerDay) {
        ItemValidator.validateOwnerId(ownerId);
        // The validator returns the cleaned-up (trimmed) text, so we store exactly what was checked
        String name = ItemValidator.validateName(itemName);
        String cleanDescription = ItemValidator.validateDescription(description);
        ItemValidator.validateCostPerDay(costPerDay);

        ItemDomain item = new ItemDomain();
        item.setOwnerId(ownerId);
        item.setItemName(name);
        item.setDescription(cleanDescription);
        item.setCostPerDay(costPerDay);
        item.setStatus(ItemStatus.available);
        item.setCreatedAt(LocalDateTime.now());

        itemRepository.save(item);
        return item;
    }

    // ---------- read ----------

    public Optional<ItemDomain> getItemById(int itemId) {
        validateItemId(itemId);
        return itemRepository.findByItemId(itemId);
    }

    public ItemDomain requireItemById(int itemId) {
        return getItemById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
    }

    public List<ItemDomain> getItemsByOwner(int ownerId) {
        validateOwnerId(ownerId);
        return itemRepository.findAll().stream()
                .filter(i -> i.getOwnerId() == ownerId)
                .toList();
    }

    /** The item plus the owner's username, for the detail view. */
    public ItemDetail getItemDetail(int itemId) {
        ItemDomain item = requireItemById(itemId);
        String owner = users.getUser(item.getOwnerId()).getUsername();
        return new ItemDetail(item, owner);
    }

    // ---------- status changes ----------

    /** Available or rented becomes unlisted. A rented item stays out until it is returned. */
    public ItemDomain delist(int itemId) {
        return tx.inTransaction(() -> {
            ItemDomain item = requireItemById(itemId);
            moveTo(item, ItemStatus.unlisted);
            itemRepository.update(item);
            return item;
        });
    }

    /** Unlisted becomes available, unless the item is still out on a rental. */
    public ItemDomain relist(int itemId) {
        return tx.inTransaction(() -> {
            ItemDomain item = requireItemById(itemId);
            if (rentals.findActiveByItem(itemId).isPresent()) {
                throw new BusinessRuleException(
                        "Item is still out on a rental. Confirm the return first: " + item.getItemName());
            }
            moveTo(item, ItemStatus.available);
            itemRepository.update(item);
            return item;
        });
    }

    private void moveTo(ItemDomain item, ItemStatus next) {
        if (!item.getStatus().canTransitionTo(next)) {
            throw new BusinessRuleException(
                    "Cannot change " + item.getItemName() + " from " + item.getStatus() + " to " + next);
        }
        item.setStatus(next);
    }

    // ---------- validation ----------

    private void validateItemId(int itemId) {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be positive");
    }

    private void validateOwnerId(int ownerId) {
        if (ownerId <= 0) throw new IllegalArgumentException("ownerId must be positive");
    }
}