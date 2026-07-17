package org.desp.mining.gui;

import com.binggre.binggreapi.utils.ColorManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.skill.MiningSkillService;
import org.desp.mining.skill.MiningSkillType;
import org.desp.mining.utils.MiningBagService;
import org.jetbrains.annotations.NotNull;

public class MiningSkillGUI implements InventoryHolder {

    public static final Map<Integer, MiningSkillType> SLOT_TO_SKILL = Map.of(
            11, MiningSkillType.NO_FATIGUE,
            13, MiningSkillType.DOUBLE_DROP,
            15, MiningSkillType.RARE_ORES
    );
    private static final int INFO_SLOT = 4;

    // 파스텔톤 팔레트
    private static final String PASTEL_YELLOW = "#FDFFB6";
    private static final String PASTEL_RED = "#FFADAD";
    private static final String PASTEL_PEACH = "#FFD6A5";
    private static final String PASTEL_BLUE = "#A5D8FF";
    private static final String SOFT_WHITE = "#EDEDED";
    private static final String SOFT_GRAY = "#C9C9C9";
    private static final String DIM_GRAY = "#9E9E9E";

    private final Inventory inventory;

    private MiningSkillGUI() {
        this.inventory = Bukkit.createInventory(this, 27, "§8[ 채광 스킬 ]");
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player, MiningDto miningData) {
        MiningSkillGUI gui = new MiningSkillGUI();
        gui.refresh(miningData);
        player.openInventory(gui.getInventory());
    }

    public void refresh(MiningDto miningData) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(INFO_SLOT, createInfoItem(miningData));
        inventory.setItem(11, createSkillItem(miningData, MiningSkillType.NO_FATIGUE, Material.REDSTONE, PASTEL_RED));
        inventory.setItem(13, createSkillItem(miningData, MiningSkillType.DOUBLE_DROP, Material.NETHERITE_PICKAXE, PASTEL_PEACH));
        inventory.setItem(15, createSkillItem(miningData, MiningSkillType.RARE_ORES, Material.DIAMOND, PASTEL_BLUE));
    }

    private ItemStack createInfoItem(MiningDto miningData) {
        int level = miningData.getLevel();
        boolean maxLevel = level >= MiningSkillService.getMaxLevel();

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (maxLevel) {
            lore.add(ColorManager.format(SOFT_WHITE + "   채광 레벨 : " + PASTEL_YELLOW + " Lv." + level + " " + PASTEL_PEACH + " (최대)"));
        } else {
            double exp = Math.floor(miningData.getExp() * 10) / 10.0;
            lore.add(ColorManager.format(SOFT_WHITE + "   채광 레벨 : " + PASTEL_YELLOW + " Lv." + level));
            lore.add(ColorManager.format(SOFT_WHITE + "   경험치 : " + PASTEL_YELLOW + " " + exp + " " + DIM_GRAY + " / " + MiningSkillService.getRequiredExp(level)));
        }
        lore.add(ColorManager.format(SOFT_WHITE + "   보유 스킬 포인트 : " + PASTEL_YELLOW + " " + miningData.getSkillPoints() + "P"));
        lore.add("");
        lore.add(ColorManager.format(DIM_GRAY + "   채광을 하면 경험치를 얻고, 레벨업 시"));
        lore.add(ColorManager.format(DIM_GRAY + "   스킬 포인트를 획득합니다."));

        return createItem(Material.EXPERIENCE_BOTTLE,
                ColorManager.format(PASTEL_YELLOW + " 채광 레벨 정보"), lore);
    }

    private ItemStack createSkillItem(MiningDto miningData, MiningSkillType type, Material material, String color) {
        int skillLevel = miningData.getSkillLevel(type.name());
        int maxLevel = MiningSkillService.getSkillMaxLevel(type);

        List<String> lore = new ArrayList<>();
        lore.add(ColorManager.format(SOFT_GRAY + "   " + type.getDescription()));
        if (type == MiningSkillType.RARE_ORES) {
            for (String itemId : MiningSkillService.getRareOreItemIds()) {
                lore.add(ColorManager.format(PASTEL_BLUE + "    - ") + MiningBagService.displayNameOf(itemId));
            }
        }
        lore.add("");
        lore.add(ColorManager.format(SOFT_WHITE + "   레벨 : " + PASTEL_YELLOW + " " + skillLevel + " " + DIM_GRAY + " / " + maxLevel));
        lore.add(ColorManager.format(SOFT_WHITE + "   현재 효과 : ") + MiningSkillService.describeEffect(type, skillLevel));
        if (skillLevel >= maxLevel) {
            lore.add("");
            lore.add(ColorManager.format(PASTEL_PEACH + "   최대 레벨을 달성했습니다!"));
        } else {
            lore.add(ColorManager.format(SOFT_WHITE + "   다음 효과 : ") + MiningSkillService.describeEffect(type, skillLevel + 1));
            lore.add(ColorManager.format(SOFT_WHITE + "   필요 포인트 : " + PASTEL_YELLOW + " " + MiningSkillService.getPointCost(type, skillLevel + 1) + "P "
                    + DIM_GRAY + " (보유: " + miningData.getSkillPoints() + "P)"));
            lore.add("");
            lore.add(ColorManager.format(PASTEL_YELLOW + "   클릭 " + DIM_GRAY + " - 1레벨 투자"));
            lore.add(ColorManager.format(PASTEL_YELLOW + "   쉬프트 클릭 " + DIM_GRAY + " - 가능한 만큼 모두 투자"));
        }

        return createItem(material, ColorManager.format(color + " " + type.getDisplayName()), lore);
    }

    private ItemStack createItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }
}
