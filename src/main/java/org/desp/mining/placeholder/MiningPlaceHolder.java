package org.desp.mining.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.Indyuce.mmocore.api.MMOCoreAPI;
import org.bukkit.entity.Player;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.skill.MiningSkillService;
import org.desp.mining.skill.MiningSkillType;
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
        var miningData = MiningRepository.getInstance().getPlayerData(player);
        if (miningData == null) {
            return "";
        }
        if(identifier.equals("fatigue")){
            return miningData.getFatigue()+"";
        }
        if(identifier.equals("level")){
            return miningData.getLevel()+"";
        }
        if(identifier.equals("exp")){
            return miningData.getExp()+"";
        }
        if(identifier.equals("required_exp")){
            return MiningSkillService.getRequiredExp(miningData.getLevel())+"";
        }
        if(identifier.equals("skill_points")){
            return miningData.getSkillPoints()+"";
        }
        for (MiningSkillType type : MiningSkillType.values()) {
            if (identifier.equalsIgnoreCase("skill_" + type.name())) {
                return miningData.getSkillLevel(type.name())+"";
            }
        }
        return "";
    }
}
