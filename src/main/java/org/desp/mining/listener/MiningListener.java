package org.desp.mining.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.desp.IDEPass.api.IDEPassAPI;
import org.desp.IDEPass.dto.IDEPassUserDataDto;
import org.desp.mining.Mining;
import org.desp.mining.command.MiningEventCommand;
import org.desp.mining.database.MiningBagRepository;
import org.desp.mining.database.MiningItemRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningBagDto;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.dto.MiningItemDto;
import org.desp.mining.event.MiningEvent;
import org.desp.mining.skill.MiningSkillService;
import org.desp.mining.skill.MiningSkillType;
import org.desp.mining.utils.MiningBagItems;
import org.swlab.etcetera.EtCetera;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static org.desp.mining.utils.MiningUtils.getMaterials;

public class MiningListener implements Listener {

    private final MiningRepository miningRepository = MiningRepository.getInstance();
    private final MiningItemRepository itemRepository = MiningItemRepository.getInstance();

    private final Cache<Player, Integer> macro = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.SECONDS)
            .build();

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Mongo 에 영구 저장한다. (저장 큐가 순서와 완료를 보장한다)
        miningRepository.savePlayerDataOnQuit(event.getPlayer());

        String uuid = event.getPlayer().getUniqueId().toString();
        MiningBagDto bag = MiningBagRepository.getInstance().getBagCache().remove(uuid);
        if (bag != null) {
            MiningBagRepository.getInstance().saveBagOnQuit(uuid, bag);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

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
            MiningDto miningData = miningRepository.getPlayerData(player);
            // DataLoadEvent 로드가 끝나기 전(또는 로드 실패)에는 채광을 막는다.
            // 이 확인이 없으면 아래 피로도 확인이 엉뚱한 안내를 하거나 보상 로직이 NPE 를 낸다.
            if (miningData == null) {
                player.sendTitle("", "§7채광 데이터를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", 0, 20, 5);
                return;
            }
            double fatigue = miningData.getFatigue();

            IDEPassUserDataDto player1 = IDEPassAPI.getPlayer(uuid);
            boolean activate = player1.isActivate();


            if (fatigue >= 100) {
                macroLog(player);
                player.sendMessage("§c 피로도가 가득 찼습니다! 채광이 불가능합니다.");
                return;
            }
            ItemStack rewardItem = getRandomDropItem(miningData);
            if (rewardItem == null) {
                return;
            }
            List<String> triggeredSkills = new ArrayList<>();
            int dropAmount = MiningSkillService.rollDoubleDrop(miningData) ? 2 : 1;
            if (dropAmount > 1) {
                triggeredSkills.add("§6더블 드랍");
            }
            String doubleDropSuffix = dropAmount > 1 ? " §6§o(더블 드랍!)" : "";
            // 채광 이벤트 진행 중이면 모든 계산이 끝난 최종 보상 개수에 +1
            boolean eventBonus = MiningEventCommand.isEventActive();
            if (eventBonus) {
                dropAmount += 1;
            }
            String eventSuffix = eventBonus ? " §e§o(이벤트 +1)" : "";
            rewardItem.setAmount(dropAmount);
            String id = MMOItems.getID(rewardItem);
            String rewardDisplayName = rewardItem.getItemMeta().getDisplayName();
            String dropActionBar;
            boolean bagItem = MiningBagItems.isBagItem(id);
            if (bagItem) {
                MiningBagDto bag = MiningBagRepository.getInstance().getBagCache().get(uuid);
                if (bag != null) {
                    bag.addCount(id, dropAmount);
                    dropActionBar = "§f" + rewardDisplayName + " §7§ox2 §7(가방 +" + dropAmount + " (/광물가방))" + doubleDropSuffix + eventSuffix;
                } else {
                    // 가방이 아직 로드되지 않았거나 로드에 실패한 상태.
                    // 가방에 적립한 척하면 그대로 유실되므로 인벤토리로 지급한다.
                    bagItem = false;
                    player.getInventory().addItem(rewardItem);
                    dropActionBar = "§f" + rewardDisplayName + " §7§ox" + dropAmount + doubleDropSuffix + eventSuffix;
                }
            } else {
                player.getInventory().addItem(rewardItem);
                dropActionBar = "§f" + rewardDisplayName + " §7§ox" + dropAmount + doubleDropSuffix + eventSuffix;
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
            if (MiningSkillService.rollNoFatigue(miningData)) {
                triggeredSkills.add("§b피로도 무효");
            } else {
                addFatigue(player, additionalFatigue);
            }
            MiningSkillService.addMiningExp(player, miningData);
            player.sendActionBar(dropActionBar + " §8| " + levelStatusOf(miningData));

            if (!triggeredSkills.isEmpty()) {
                player.sendTitle("", String.join("§f, ", triggeredSkills) + " §f효과가 발동했습니다!", 0, 25, 10);
            }
        }
    }

    private String levelStatusOf(MiningDto miningData) {
        int level = miningData.getLevel();
        if (level >= MiningSkillService.getMaxLevel()) {
            return "§eLv." + level + " §6(최대)";
        }
        double exp = Math.floor(miningData.getExp() * 10) / 10.0;
        double requiredExp = MiningSkillService.getRequiredExp(level);
        return "§eLv." + level + " §7( 경험치 §f" + exp + " §7/ " + requiredExp + " )";
    }

    public static void addFatigue(Player player, double amount) {
        // 저장소가 캐시 변이를 처리한다. (로드 실패 시 null 안전)
        MiningRepository.getInstance().addFatigue(player, amount);
    }

    public static void reduceFatigue(Player player, double amount) {
        MiningRepository.getInstance().reduceFatigue(player, amount);
    }

    private void macroLog(Player player) {
        int oldCount = Optional.ofNullable(macro.getIfPresent(player)).orElse(0);
        int newCount = oldCount + 1;

        macro.put(player, newCount);
        if (newCount % 10 == 0) {
            player.teleport(new Location(Bukkit.getWorld("world"), -21.8, 37.0, -731.425, -89.8F, 4.9F));
        }
    }


    private ItemStack getRandomDropItem(MiningDto miningData) {
        Map<String, MiningItemDto> miningCache = itemRepository.getMiningCache();

        // RARE_ORES 스킬: 지정된 아이템의 가중치를 n% 올리고 나머지를 n% 내린 뒤,
        // 새 총합 기준으로 추첨하여 전체 확률 합은 항상 100%를 유지한다.
        int rareSkillLevel = miningData == null ? 0
                : miningData.getSkillLevel(MiningSkillType.RARE_ORES.name());
        double boost = MiningSkillService.getRareOreBoostPercent(rareSkillLevel) / 100.0;
        Set<String> rareItemIds = new HashSet<>(MiningSkillService.getRareOreItemIds());

        Map<String, Double> weights = new LinkedHashMap<>();
        double totalWeight = 0;
        for (Entry<String, MiningItemDto> entry : miningCache.entrySet()) {
            double weight = entry.getValue().getItemDropPercentage();
            if (boost > 0 && !rareItemIds.isEmpty()) {
                weight *= rareItemIds.contains(entry.getKey())
                        ? (1 + boost)
                        : Math.max(0, 1 - boost);
            }
            weights.put(entry.getKey(), weight);
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return null;
        }

        Random random = new Random();
        double randomValue = random.nextDouble(totalWeight);
        double cumulativeProbability = 0;

        for (Entry<String, Double> entry : weights.entrySet()) {
            cumulativeProbability += entry.getValue();
            if (cumulativeProbability >= randomValue) {
                return MMOItems.plugin.getItem(Type.MISCELLANEOUS, entry.getKey());
            }
        }
        return null;
    }
}
