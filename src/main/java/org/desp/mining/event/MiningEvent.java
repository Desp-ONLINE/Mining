package org.desp.mining.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MiningEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private Player player;
    private ItemStack resultItem;

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public MiningEvent(Player player, ItemStack resultItem) {
        this.player = player;
        this.resultItem = resultItem;
    }

    public Player getPlayer() {
        return this.player;
    }

    public ItemStack getResultItem() {
        return this.resultItem;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
