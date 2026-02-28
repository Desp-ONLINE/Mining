package org.desp.mining.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.jetbrains.annotations.NotNull;

public class ReduceFatigueCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
                             @NotNull String[] strings) {

        Player player = (Player) commandSender;
        if(!player.isOp()){
            return false;
        }

        String playerName = player.getName();
        int reduceAmount = Integer.parseInt(strings[0]);
        if (MiningRepository.getInstance().exitsPlayer(playerName)) {
            MiningDto userDto = MiningRepository.getInstance().getPlayer(playerName);
            if (userDto == null) {
                player.sendMessage("피로도 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
                return true;
            }
            player.sendMessage("§a피로 회복제를 마셔 §7§o-" + reduceAmount + "% §a만큼 피로도가 감소했습니다.");
            MiningRepository.getInstance().reduceFatigue(player, reduceAmount);
        } else {
            player.sendMessage(ChatColor.RED + "존재하지 않는 플레이어입니다");
            return true;
        }
        return true;
    }
}
