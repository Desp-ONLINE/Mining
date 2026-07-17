package org.desp.mining.command;

import com.binggre.binggreapi.utils.ColorManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.skill.MiningSkillService;
import org.desp.mining.skill.MiningSkillType;
import org.jetbrains.annotations.NotNull;

public class MiningAdminCommand implements TabExecutor {

    private static final List<String> SUB_COMMANDS = Arrays.asList("레벨", "경험치", "스킬포인트", "스킬", "정보");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s,
                             @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(ColorManager.format("#FF8888 권한이 없습니다."));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ColorManager.format("#FF8888 접속 중인 플레이어가 아닙니다: " + args[1]));
            return true;
        }

        MiningDto miningData = MiningRepository.getInstance().getPlayerData(target);
        if (miningData == null) {
            sender.sendMessage(ColorManager.format("#FF8888 해당 플레이어의 채광 정보를 불러올 수 없습니다."));
            return true;
        }

        switch (args[0]) {
            case "정보" -> sendInfo(sender, target, miningData);
            case "레벨" -> setLevel(sender, target, miningData, args);
            case "경험치" -> setExp(sender, target, miningData, args);
            case "스킬포인트" -> setSkillPoints(sender, target, miningData, args);
            case "스킬" -> setSkillLevel(sender, target, miningData, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 관리 ] §f사용법"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광관리 레벨 <플레이어> <레벨>"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광관리 경험치 <플레이어> <경험치>"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광관리 스킬포인트 <플레이어> <포인트>"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광관리 스킬 <플레이어> <피로무효/더블드랍/희귀광물> <레벨>"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광관리 정보 <플레이어>"));
    }

    private void sendInfo(CommandSender sender, Player target, MiningDto miningData) {
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 관리 ] §e" + target.getName() + " §f채광 정보"));
        sender.sendMessage(ColorManager.format("#C9C9C9  레벨: §f" + miningData.getLevel()
                + " §7/ 경험치: §f" + miningData.getExp()
                + " §7/ 스킬 포인트: §f" + miningData.getSkillPoints() + "P"));
        for (MiningSkillType type : MiningSkillType.values()) {
            sender.sendMessage(ColorManager.format("#C9C9C9  " + type.getDisplayName()
                    + ": §fLv." + miningData.getSkillLevel(type.name())
                    + " §7/ 최대 Lv." + MiningSkillService.getSkillMaxLevel(type)));
        }
    }

    private void setLevel(CommandSender sender, Player target, MiningDto miningData, String[] args) {
        Integer value = parseInt(sender, args, 2, "레벨");
        if (value == null) {
            return;
        }
        int maxLevel = MiningSkillService.getMaxLevel();
        if (value < 1 || value > maxLevel) {
            sender.sendMessage(ColorManager.format("#FF8888 레벨은 1 ~ " + maxLevel + " 사이로 입력해주세요."));
            return;
        }
        miningData.setLevel(value);
        save(target);
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 관리 ] §e" + target.getName()
                + " §f채광 레벨을 §e" + value + " §f(으)로 설정했습니다."));
    }

    private void setExp(CommandSender sender, Player target, MiningDto miningData, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ColorManager.format("#FF8888 사용법: /채광관리 경험치 <플레이어> <경험치>"));
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorManager.format("#FF8888 경험치는 숫자로 입력해주세요."));
            return;
        }
        if (value < 0) {
            sender.sendMessage(ColorManager.format("#FF8888 경험치는 0 이상으로 입력해주세요."));
            return;
        }
        miningData.setExp(value);
        save(target);
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 관리 ] §e" + target.getName()
                + " §f채광 경험치를 §e" + value + " §f(으)로 설정했습니다."));
    }

    private void setSkillPoints(CommandSender sender, Player target, MiningDto miningData, String[] args) {
        Integer value = parseInt(sender, args, 2, "스킬 포인트");
        if (value == null) {
            return;
        }
        if (value < 0) {
            sender.sendMessage(ColorManager.format("#FF8888 스킬 포인트는 0 이상으로 입력해주세요."));
            return;
        }
        miningData.setSkillPoints(value);
        save(target);
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 관리 ] §e" + target.getName()
                + " §f스킬 포인트를 §e" + value + "P §f(으)로 설정했습니다."));
    }

    private void setSkillLevel(CommandSender sender, Player target, MiningDto miningData, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ColorManager.format("#FF8888 사용법: /채광관리 스킬 <플레이어> <피로무효/더블드랍/희귀광물> <레벨>"));
            return;
        }
        MiningSkillType type = MiningSkillType.fromInput(args[2]);
        if (type == null) {
            sender.sendMessage(ColorManager.format("#FF8888 알 수 없는 스킬입니다: " + args[2]));
            return;
        }
        Integer value = parseInt(sender, args, 3, "스킬 레벨");
        if (value == null) {
            return;
        }
        int maxLevel = MiningSkillService.getSkillMaxLevel(type);
        if (value < 0 || value > maxLevel) {
            sender.sendMessage(ColorManager.format("#FF8888 스킬 레벨은 0 ~ " + maxLevel + " 사이로 입력해주세요."));
            return;
        }
        Map<String, Integer> skills = miningData.getSkills();
        if (value == 0) {
            if (skills != null) {
                skills.remove(type.name());
            }
        } else {
            miningData.addSkillLevel(type.name(), value - miningData.getSkillLevel(type.name()));
        }
        save(target);
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 관리 ] §e" + target.getName()
                + " §f" + type.getDisplayName() + " 스킬 레벨을 §eLv." + value + " §f(으)로 설정했습니다."));
    }

    private Integer parseInt(CommandSender sender, String[] args, int index, String label) {
        if (args.length <= index) {
            sendUsage(sender);
            return null;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorManager.format("#FF8888 " + label + "은(는) 숫자로 입력해주세요."));
            return null;
        }
    }

    private void save(Player target) {
        // 관리 명령 변경분은 즉시 Mongo 저장 큐에 제출한다.
        MiningRepository.getInstance().savePlayerData(target);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s,
                                      @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equals("스킬")) {
            return filter(Arrays.asList("피로무효", "더블드랍", "희귀광물"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(input)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
