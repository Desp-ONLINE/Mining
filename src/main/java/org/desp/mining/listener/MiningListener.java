package org.desp.mining.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.desp.IDEPass.api.IDEPassAPI;
import org.desp.IDEPass.dto.IDEPassUserDataDto;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningItemRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.dto.MiningItemDto;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static org.desp.mining.utils.MiningUtils.getMaterials;

public class MiningListener implements Listener {

    private final MiningRepository miningRepository = MiningRepository.getInstance();
    private final MiningItemRepository itemRepository = MiningItemRepository.getInstance();
    public static Map<String, MiningDto> miningCache = new HashMap<>();

    private final Cache<Player, Integer> macro = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.SECONDS)
            .build();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String user_id = player.getName();
        String uuid = player.getUniqueId().toString();

        Bukkit.getScheduler().runTaskLaterAsynchronously(Mining.getInstance(), () -> {
            MiningDto playerMiningData1 = miningRepository.getPlayerMiningData(uuid, user_id);
            miningCache.put(uuid, playerMiningData1);
        }, 30L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        miningRepository.saveMining(uuid, miningCache);
        miningCache.remove(uuid);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String user_id = player.getName();

        String uuid = player.getUniqueId().toString();
        Material blockType = event.getBlock().getType();

        String itemID = MMOItems.getID(event.getPlayer().getInventory().getItemInMainHand());
        if (itemID == null) {
            return;
        }
        List<Material> materialList = getMaterials();
        if (!player.isOp()) {
            event.setCancelled(true);
        }

        if (materialList.contains(blockType) && itemID.contains("곡괭이")) {
            MiningDto miningData = miningCache.get(uuid);
            double fatigue = miningData.getFatigue();

            IDEPassUserDataDto player1 = IDEPassAPI.getPlayer(uuid);
            boolean activate = player1.isActivate();

            if (fatigue >= 100) {
                macroLog(player);
                player.sendMessage("§c 피로도가 가득 찼습니다! 채광이 불가능합니다.");
                return;
            }
            player.getInventory().addItem(getRandomDropItem());
            if (activate && "full".equals(player1.getPassType())) {
                fatigue += 0.8;
                if (fatigue >= 99.6) {
                    fatigue = 100;
                }
            } else {
                fatigue += 1;
            }
            MiningDto saveMiningDto = MiningDto.builder()
                    .user_id(user_id)
                    .uuid(uuid)
                    .fatigue(fatigue)
                    .build();
            miningCache.put(uuid, saveMiningDto);

            player.sendActionBar("§e현재 피로도: " + Math.round(fatigue * 100) / 100.0 + "%");

        }
    }

    private void macroLog(Player player) {
        int oldCount = Optional.ofNullable(macro.getIfPresent(player)).orElse(0);
        int newCount = oldCount + 1;

        macro.put(player, newCount);
        if (newCount % 10 == 0) {
            player.performCommand("spawn");
        }
    }


    private ItemStack getRandomDropItem() {
        Random random = new Random();
        int randomValue = random.nextInt(100);
        int cumulativeProbability = 0;

        Map<String, MiningItemDto> miningCache = itemRepository.getMiningCache();
        for (Entry<String, MiningItemDto> stringMiningItemDtoEntry : miningCache.entrySet()) {
            cumulativeProbability += stringMiningItemDtoEntry.getValue().getItemDropPercentage();
            if (cumulativeProbability >= randomValue) {
                String item_id = stringMiningItemDtoEntry.getKey();
                ItemStack dropItem = MMOItems.plugin.getItem(Type.MISCELLANEOUS, item_id);

                return dropItem;
            }
        }
        return null;
    }

    @EventHandler
    public void onRightClickWithPickaxe(PlayerInteractEvent event) {
        String itemID = MMOItems.getID(event.getPlayer().getInventory().getItemInMainHand());

        if (!itemID.contains("곡괭이")) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            Player player = event.getPlayer();
            if (miningCache.get(player.getUniqueId().toString()) == null) {
                return;
            }
            MiningDto miningData = miningCache.get(player.getUniqueId().toString());
            player.sendActionBar("§a현재 피로도: " + Math.round(miningData.getFatigue() * 100) / 100.0 + "%");
        }
    }
}
