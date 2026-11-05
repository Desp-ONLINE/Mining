package org.desp.mining.command;

import com.binggre.binggreapi.utils.ColorManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.desp.mining.utils.MiningBagItems;
import org.desp.mining.utils.MiningBagService;
import org.jetbrains.annotations.NotNull;

public class MiningBagCommand implements TabExecutor {

    private static final List<String> SUB_COMMANDS = List.of("판매", "회수", "넣기");

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,
                             @NotNull String[] args) {
        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length == 0) {
            MiningBagService.showBag(player);
            return true;
        }

        switch (args[0]) {
            case "판매" -> MiningBagService.sellAll(player);
            case "넣기", "보관" -> {
                String itemId = null;
                if (args.length >= 2) {
                    itemId = MiningBagItems.resolveAlias(args[1]);
                    if (itemId == null) {
                        player.sendMessage(ColorManager.format("#FF8888 알 수 없는 광물입니다: " + args[1]));
                        return true;
                    }
                }
                MiningBagService.deposit(player, itemId);
            }
            case "회수" -> {
                if (args.length < 2) {
                    player.sendMessage(ColorManager.format("#FF8888 사용법: /광물가방 회수 [철/금/청금석/다이아몬드/에메랄드/일반/레어/에픽/유니크/레전더리/네더라이트/퓨전스틸] (개수)"));
                    return true;
                }
                String itemId = MiningBagItems.resolveAlias(args[1]);
                if (itemId == null) {
                    player.sendMessage(ColorManager.format("#FF8888 알 수 없는 광물입니다: " + args[1]));
                    return true;
                }
                if (args.length >= 3) {
                    int amount;
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ColorManager.format("#FF8888 개수는 숫자로 입력해주세요."));
                        return true;
                    }
                    MiningBagService.withdraw(player, itemId, amount);
                } else {
                    MiningBagService.withdraw(player, itemId);
                }
            }
            default -> MiningBagService.showBag(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equals("넣기") || args[0].equals("보관") || args[0].equals("회수"))) {
            return filter(MiningBagItems.getAliases(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(input)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
