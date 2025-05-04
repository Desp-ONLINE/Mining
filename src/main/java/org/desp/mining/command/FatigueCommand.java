package org.desp.mining.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.jetbrains.annotations.NotNull;

public class FatigueCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
                             @NotNull String[] strings) {

        Player player = (Player) commandSender;

        String playerName = strings[0];
        if (MiningRepository.getInstance().exitsPlayer(playerName)) {
            MiningDto userDto = MiningRepository.getInstance().getPlayer(playerName);
            if (userDto == null) {
                player.sendMessage("피로도 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
                return true;
            }
            double fatigue = userDto.getFatigue();
            player.sendMessage("§e" + playerName + "§f의 피로도 : §c" + fatigue);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "존재하지 않는 플레이어입니다");
            return true;
        }
    }
}
