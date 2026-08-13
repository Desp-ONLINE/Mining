package org.desp.mining.gui;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.skill.MiningSkillService;
import org.desp.mining.skill.MiningSkillType;

public class MiningSkillGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MiningSkillGUI gui)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        boolean resetClick = event.getSlot() == MiningSkillGUI.RESET_SLOT;
        MiningSkillType type = MiningSkillGUI.SLOT_TO_SKILL.get(event.getSlot());
        if (type == null && !resetClick) {
            return;
        }

        MiningDto miningData = MiningRepository.getInstance().getPlayerData(player);
        if (miningData == null) {
            player.sendMessage("§c채광 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
            player.closeInventory();
            return;
        }

        if (resetClick) {
            handleReset(player, miningData, event.isShiftClick(), gui);
            return;
        }

        int currentLevel = miningData.getSkillLevel(type.name());
        int maxLevel = MiningSkillService.getSkillMaxLevel(type);
        if (currentLevel >= maxLevel) {
            player.playSound(player, "minecraft:entity.villager.no", SoundCategory.AMBIENT, 1, 1);
            return;
        }

        int levels = event.isShiftClick() ? Integer.MAX_VALUE : 1;
        int pointsBefore = miningData.getSkillPoints();
        int levelUps = MiningSkillService.invest(miningData, type, levels);

        if (levelUps <= 0) {
            player.playSound(player, "minecraft:entity.villager.no", SoundCategory.AMBIENT, 1, 1);
            player.sendMessage("§c스킬 포인트가 부족합니다. §7§o(필요: "
                    + MiningSkillService.getPointCost(type, currentLevel + 1)
                    + "P, 보유: " + miningData.getSkillPoints() + "P)");
        } else {
            int usedPoints = pointsBefore - miningData.getSkillPoints();
            int newLevel = miningData.getSkillLevel(type.name());
            player.playSound(player, "minecraft:entity.player.levelup", SoundCategory.AMBIENT, 1, 1.5f);
            player.sendMessage("§7[채광 스킬] §e" + type.getDisplayName() + " §f스킬이 §eLv." + newLevel
                    + " §f이(가) 되었습니다! §7§o(포인트 " + usedPoints + "P 사용, 남은 포인트: "
                    + miningData.getSkillPoints() + "P)");
        }

        gui.refresh(miningData);
    }

    private void handleReset(Player player, MiningDto miningData, boolean shiftClick, MiningSkillGUI gui) {
        if (!shiftClick) {
            player.playSound(player, "minecraft:entity.villager.no", SoundCategory.AMBIENT, 1, 1);
            player.sendMessage("§7[채광 스킬] §f레벨 초기화는 §e쉬프트 클릭§f으로만 진행할 수 있습니다.");
            return;
        }

        int owned = MiningSkillService.countResetItems(player);
        if (owned < MiningSkillService.RESET_ITEM_COST) {
            player.playSound(player, "minecraft:entity.villager.no", SoundCategory.AMBIENT, 1, 1);
            player.sendMessage("§c" + MiningSkillService.getResetItemName() + "§c이(가) 부족합니다. §7§o(필요: "
                    + MiningSkillService.RESET_ITEM_COST + "개, 보유: " + owned + "개)");
            return;
        }

        if (!MiningSkillService.consumeResetItems(player)) {
            player.playSound(player, "minecraft:entity.villager.no", SoundCategory.AMBIENT, 1, 1);
            player.sendMessage("§c아이템 소모에 실패했습니다. 다시 시도해주세요.");
            return;
        }

        MiningSkillService.resetLevel(miningData);
        // 초기화는 되돌릴 수 없으므로 즉시 DB 에 반영한다.
        MiningRepository.getInstance().savePlayerData(player);

        player.playSound(player, "minecraft:entity.player.levelup", SoundCategory.AMBIENT, 1, 0.7f);
        player.sendMessage("");
        player.sendMessage("§7[채광 스킬] §f" + MiningSkillService.getResetItemName() + " §e"
                + MiningSkillService.RESET_ITEM_COST + "개§f를 소모하여 채광 레벨을 초기화했습니다.");
        player.sendMessage("");

        gui.refresh(miningData);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MiningSkillGUI) {
            event.setCancelled(true);
        }
    }
}
