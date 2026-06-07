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
import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.desp.IDEPass.api.IDEPassAPI;
import org.desp.IDEPass.dto.IDEPassUserDataDto;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningBagRepository;
import org.desp.mining.database.MiningItemRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningBagDto;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.dto.MiningItemDto;
import org.desp.mining.event.MiningEvent;
import org.desp.mining.utils.MiningBagItems;
import org.swlab.etcetera.EtCetera;

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

            MiningBagDto bag = MiningBagRepository.getInstance().getPlayerBag(uuid, user_id);
            MiningBagRepository.getInstance().getBagCache().put(uuid, bag);
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        miningRepository.saveMining(uuid, miningCache);
        miningCache.remove(uuid);

        MiningBagDto bag = MiningBagRepository.getInstance().getBagCache().remove(uuid);
        if (bag != null) {
            MiningBagRepository.getInstance().saveBag(uuid, bag);
        }
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
        if(!player.getWorld().getName().equals("world") && !EtCetera.getChannelType().equals("lobby")) {
            player.sendMessage("§c 채광할 수 있는 장소가 아닙니다. /채광");
            event.setCancelled(true);
            return;
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
            ItemStack rewardItem = getRandomDropItem();
            rewardItem.setAmount(1);
            String id = MMOItems.getID(rewardItem);
            String rewardDisplayName = rewardItem.getItemMeta().getDisplayName();
            if (MiningBagItems.isBagItem(id)) {
                MiningBagDto bag = MiningBagRepository.getInstance().getBagCache().get(uuid);
                if (bag != null) {
                    bag.addCount(id, 1);
                }
                player.sendActionBar("§f" + rewardDisplayName + " §7§ox2 §7(가방 +1 (/광물가방))");
            } else {
                player.getInventory().addItem(rewardItem);
                player.sendActionBar("§f" + rewardDisplayName + " §7§ox1");
            }
            Bukkit.getPluginManager().callEvent(new MiningEvent(player, rewardItem));

            double itemDropPercentage = MiningItemRepository.getInstance().getMiningCache().get(id).getItemDropPercentage();
            if (itemDropPercentage < 1) {
                player.sendMessage("§f  " + rewardDisplayName + "§f을 획득했습니다!");
            }
            Material type = player.getInventory().getItemInMainHand().getType();
            double additionalFatigue = 1.0;
            if (type.equals(Material.IRON_PICKAXE)) {
                additionalFatigue = 0.6;
            } else if (type.equals(Material.DIAMOND_PICKAXE)) {
                additionalFatigue = 0.5;
            } else if (type.equals(Material.NETHERITE_PICKAXE)) {
                additionalFatigue = 0.4;
            }
            if (activate) {
                additionalFatigue -= additionalFatigue / 10;
            }
            addFatigue(player, additionalFatigue);
        }
    }

    public static void addFatigue(Player player, double amount) {
        String uuid = player.getUniqueId().toString();
        MiningDto miningData = miningCache.get(uuid);
        double fatigue = miningData.getFatigue();
//        System.out.println("fatigue = " + fatigue);
        if (fatigue + amount >= 100) {
            fatigue = 100;
        }
        fatigue += amount;
        MiningDto saveMiningDto = MiningDto.builder()
                .user_id(player.getName())
                .uuid(uuid)
                .fatigue(fatigue)
                .build();
        miningCache.put(uuid, saveMiningDto);
    }

    public static void reduceFatigue(Player player, double amount) {
        String uuid = player.getUniqueId().toString();
        MiningDto miningData = miningCache.get(uuid);
        double fatigue = miningData.getFatigue();
        if (fatigue - amount <= 0) {
            fatigue = 0;
        }
        fatigue -= amount;
        MiningDto saveMiningDto = MiningDto.builder()
                .user_id(player.getName())
                .uuid(uuid)
                .fatigue(fatigue)
                .build();
        miningCache.put(uuid, saveMiningDto);
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
        double randomValue = random.nextDouble(100);
        double cumulativeProbability = 0;

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

//    @EventHandler
//    public void onRightClickWithPickaxe(PlayerInteractEvent event) {
//        String itemID = MMOItems.getID(event.getPlayer().getInventory().getItemInMainHand());
//
//        if (!itemID.contains("곡괭이")) {
//            return;
//        }
//        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
//
//            Player player = event.getPlayer();
//            if (miningCache.get(player.getUniqueId().toString()) == null) {
//                return;
//            }
//            MiningDto miningData = miningCache.get(player.getUniqueId().toString());
//            player.sendActionBar("§§7[채광] §e현재 피로도: §f" + Math.round(miningData.getFatigue() * 100) / 100.0 + "%");
//        }
//    }
}
