package org.desp.mining.command;

import com.binggre.binggreapi.utils.ColorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.desp.mining.database.MiningConfigRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.gui.MiningSkillGUI;
import org.desp.mining.skill.MiningSkillService;
import org.desp.mining.skill.MiningSkillType;
import org.jetbrains.annotations.NotNull;

public class MiningSkillCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
                             @NotNull String[] args) {
        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length >= 1 && args[0].equals("리로드")) {
            if (!player.isOp()) {
                player.sendMessage(ColorManager.format("#FF8888 권한이 없습니다."));
                return true;
            }
            MiningConfigRepository.getInstance().reload();
            player.sendMessage(ColorManager.format("#FFF285  [ 채광 스킬 ] §f설정을 DB에서 다시 불러왔습니다."));
            return true;
        }

        MiningDto miningData = MiningRepository.getInstance().getPlayerData(player);
        if (miningData == null) {
            player.sendMessage(ColorManager.format("#FF8888 채광 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요."));
            return true;
        }

        if (args.length == 0) {
            MiningSkillGUI.open(player, miningData);
            return true;
        }

        if (args[0].equals("투자")) {
            if (args.length < 2) {
                player.sendMessage(ColorManager.format("#FF8888 사용법: /채광스킬 투자 [피로무효/더블드랍/희귀광물] (레벨 수)"));
                return true;
            }
            MiningSkillType type = MiningSkillType.fromInput(args[1]);
            if (type == null) {
                player.sendMessage(ColorManager.format("#FF8888 알 수 없는 스킬입니다: " + args[1]));
                return true;
            }
            int levels = 1;
            if (args.length >= 3) {
                try {
                    levels = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorManager.format("#FF8888 레벨 수는 숫자로 입력해주세요."));
                    return true;
                }
            }
            invest(player, miningData, type, levels);
            return true;
        }

        MiningSkillGUI.open(player, miningData);
        return true;
    }

    private void invest(Player player, MiningDto miningData, MiningSkillType type, int levels) {
        int currentLevel = miningData.getSkillLevel(type.name());
        int maxLevel = MiningSkillService.getSkillMaxLevel(type);
        if (currentLevel >= maxLevel) {
            player.sendMessage(ColorManager.format("#FF8888 이미 최대 레벨입니다. §7§o(" + type.getDisplayName() + " Lv." + maxLevel + ")"));
            return;
        }

        int pointsBefore = miningData.getSkillPoints();
        int levelUps = MiningSkillService.invest(miningData, type, levels);
        if (levelUps <= 0) {
            int nextCost = MiningSkillService.getPointCost(type, currentLevel + 1);
            player.sendMessage(ColorManager.format(
                    "#FF8888 스킬 포인트가 부족합니다. §7§o(다음 레벨 필요 포인트: " + nextCost
                            + "P, 보유: " + miningData.getSkillPoints() + "P)"
            ));
            return;
        }

        int usedPoints = pointsBefore - miningData.getSkillPoints();
        int newLevel = miningData.getSkillLevel(type.name());
        player.sendMessage("");
        player.sendMessage(ColorManager.format(
                "#FFF285  [ 채광 스킬 ] §e" + type.getDisplayName() + " §f스킬이 §eLv." + newLevel
                        + " §f이(가) 되었습니다! §7§o(포인트 " + usedPoints + "P 사용, 남은 포인트: " + miningData.getSkillPoints() + "P)"
        ));
        player.sendMessage(ColorManager.format("#AAAAAA  현재 효과: " + MiningSkillService.describeEffect(type, newLevel)));
        player.sendMessage("");
    }
}
