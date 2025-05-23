package org.desp.mining.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.Indyuce.mmocore.api.MMOCoreAPI;
import org.bukkit.entity.Player;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.listener.MiningListener;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MiningPlaceHolder extends PlaceholderExpansion {

    private final Mining mining;

    public MiningPlaceHolder(Mining mining) {
        this.mining = mining;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "Mining";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Dople";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        if(identifier.equals("fatigue")){
            return MiningListener.miningCache.get(player.getUniqueId().toString()).getFatigue()+"";
        }
        return "";
    }
}
