package org.desp.mining.command;

import com.binggre.binggreapi.utils.ColorManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MiningEventCommand implements TabExecutor {

    private static final List<String> SUB_COMMANDS = Arrays.asList("시작", "종료", "상태");

    private static boolean eventActive = false;

    public static boolean isEventActive() {
        return eventActive;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s,
                             @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(ColorManager.format("#FF8888 권한이 없습니다."));
            return true;
        }

        if (args.length == 0) {
            // 인자가 없으면 켜짐/꺼짐을 토글한다.
            setEventActive(sender, !eventActive);
            return true;
        }

        switch (args[0]) {
            case "시작" -> setEventActive(sender, true);
            case "종료" -> setEventActive(sender, false);
            case "상태" -> sendStatus(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void setEventActive(CommandSender sender, boolean active) {
        if (eventActive == active) {
            sender.sendMessage(ColorManager.format("#FF8888 채광 이벤트가 이미 "
                    + (active ? "진행 중" : "종료된 상태") + "입니다."));
            return;
        }
        eventActive = active;
        if (active) {
            Bukkit.broadcastMessage(ColorManager.format("#FFF285  [ 채광 이벤트 ] §f채광 이벤트가 시작되었습니다!"));
            Bukkit.broadcastMessage(ColorManager.format("#C9C9C9  이벤트 동안 채광 보상 획득량이 #FDFFB6+1 #C9C9C9증가합니다."));
        } else {
            Bukkit.broadcastMessage(ColorManager.format("#FFF285  [ 채광 이벤트 ] §f채광 이벤트가 종료되었습니다."));
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 이벤트 ] §f현재 상태: "
                + (eventActive ? "#FDFFB6진행 중 (보상 +1)" : "#9E9E9E종료됨")));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ColorManager.format("#FFF285  [ 채광 이벤트 ] §f사용법"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광이벤트 §7- §f켜짐/꺼짐 토글"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광이벤트 시작"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광이벤트 종료"));
        sender.sendMessage(ColorManager.format("#C9C9C9  /채광이벤트 상태"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s,
                                      @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String candidate : SUB_COMMANDS) {
                if (candidate.startsWith(args[0])) {
                    result.add(candidate);
                }
            }
            return result;
        }
        return List.of();
    }
}
