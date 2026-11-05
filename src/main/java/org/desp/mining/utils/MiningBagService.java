package org.desp.mining.utils;

import com.binggre.binggreapi.utils.ColorManager;
import com.binggre.binggreapi.utils.EconomyManager;
import com.binggre.binggreapi.utils.NumberUtil;
import com.binggre.mmomail.MMOMail;
import com.binggre.mmomail.objects.Mail;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.desp.mining.database.MiningBagRepository;
import org.desp.mining.dto.MiningBagDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MiningBagService {

    public static void showBag(Player player) {
        String uuid = player.getUniqueId().toString();
        MiningBagDto dto = MiningBagRepository.getInstance().getBagCache().get(uuid);

        player.sendMessage("");
        player.sendMessage(ColorManager.format("#FFF285  [ 광물 가방 ]"));
        player.sendMessage("");
        for (String itemId : MiningBagItems.getOreIds()) {
            sendCountLine(player, dto, itemId);
        }
        player.sendMessage("");
        for (String itemId : MiningBagItems.getSpecialIds()) {
            sendCountLine(player, dto, itemId);
        }
        player.sendMessage("");
        player.sendMessage(ColorManager.format("#AAAAAA /광물가방 판매 #FFEA80가방에 있는 광물을 한 번에 판매할 수 있으며, 래디언트스톤, 네더라이트, 퓨전스틸은 판매되지 않습니다."));
        player.sendMessage(ColorManager.format("#AAAAAA /광물가방 회수 [광물/등급] (개수) #FFEA80가방의 광물을 한 번에 최대 2,000개까지 메일함으로 회수할 수 있으며, 50개마다 응집 아이템으로 묶여서 지급됩니다."));
        player.sendMessage(ColorManager.format("#AAAAAA /광물가방 넣기 (광물/등급) #FFEA80인벤토리에 있는 광물, 래디언트스톤, 네더라이트, 퓨전스틸을 응집 아이템까지 포함해서 가방에 다시 넣을 수 있습니다."));
        player.sendMessage(ColorManager.format("#7B7B7B   래디언트 스톤은 일반, 레어, 에픽, 유니크, 레전더리 중 하나의 등급으로 입력하시면 됩니다."));
        player.sendMessage("");
    }

    private static void sendCountLine(Player player, MiningBagDto dto, String itemId) {
        int count = dto == null ? 0 : dto.getCount(itemId);
        player.sendMessage(ColorManager.format(
                "#FBFFBC " + displayNameOf(itemId)
                        + " §7§o( §f" + NumberUtil.applyComma(count) + "§7§o개 보유중 )"
        ));
    }

    public static void sellAll(Player player) {
        String uuid = player.getUniqueId().toString();
        MiningBagDto dto = MiningBagRepository.getInstance().getBagCache().get(uuid);
        if (dto == null || dto.getBag() == null || dto.getBag().isEmpty()) {
            player.sendMessage(ColorManager.format("#FF8888 가방이 비어 있습니다."));
            return;
        }

        double totalPrice = 0.0;
        player.sendMessage("");
        boolean sold = false;
        for (String itemId : MiningBagItems.getOreIds()) {
            int count = dto.getCount(itemId);
            if (count <= 0) {
                continue;
            }
            ItemStack item = MMOItems.plugin.getItem(Type.MISCELLANEOUS, itemId);
            if (item == null) {
                continue;
            }
            item.setAmount(1);
            double unitPrice = ShopGuiPlusApi.getItemStackPriceSell(item);
            double linePrice = unitPrice * count;
            totalPrice += linePrice;
            sold = true;
            player.sendMessage(
                    ColorManager.format("#FBFFBC 판매: " + displayNameOf(itemId))
                            + "§f x" + count
                            + " §7§o( " + NumberUtil.applyComma(linePrice) + " 골드 )"
            );
            dto.clearCount(itemId);
        }

        if (!sold) {
            player.sendMessage(ColorManager.format("#FF8888 가방이 비어 있습니다."));
            return;
        }

        EconomyManager.addMoney(player, totalPrice);
        MiningBagRepository.getInstance().saveBag(uuid, dto);

        player.sendMessage("");
        player.sendMessage(ColorManager.format(
                "#FFF285  [ 광물 가방 일괄 판매 ] §f가방의 광물을 모두 판매하여 §6"
                        + NumberUtil.applyComma(totalPrice)
                        + " §f만큼의 골드를 획득하였습니다."
        ));
        player.sendMessage("");

        System.out.println(player.getName() + " 님께서 광물가방 일괄 판매: " + totalPrice);
    }

    public static void withdraw(Player player, String itemId, int amount) {
        String uuid = player.getUniqueId().toString();
        MiningBagDto dto = MiningBagRepository.getInstance().getBagCache().get(uuid);
        if (dto == null) {
            player.sendMessage(ColorManager.format("#FF8888 가방 정보를 불러올 수 없습니다."));
            return;
        }
        int owned = dto.getCount(itemId);
        if (owned <= 0) {
            player.sendMessage(ColorManager.format("#FF8888 회수할 광물이 없습니다."));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(ColorManager.format("#FF8888 1개 이상으로 입력해주세요."));
            return;
        }
        if (amount > MiningBagItems.MAX_WITHDRAW) {
            player.sendMessage(ColorManager.format(
                    "#FF8888 한 번에 최대 " + NumberUtil.applyComma(MiningBagItems.MAX_WITHDRAW) + "개까지만 회수할 수 있습니다."
            ));
            return;
        }
        if (amount > owned) {
            player.sendMessage(ColorManager.format(
                    "#FF8888 보유 수량보다 많습니다. §7§o(보유: " + owned + "개)"
            ));
            return;
        }

        // 50개마다 응집 아이템 1개로 묶고, 남는 수량은 낱개로 지급한다.
        int condensedAmount = amount / MiningBagItems.CONDENSE_SIZE;
        int looseAmount = amount % MiningBagItems.CONDENSE_SIZE;

        ItemStack looseItem = MMOItems.plugin.getItem(Type.MISCELLANEOUS, itemId);
        ItemStack condensedItem = MMOItems.plugin.getItem(Type.MISCELLANEOUS, MiningBagItems.condensedIdOf(itemId));
        if ((looseAmount > 0 && looseItem == null) || (condensedAmount > 0 && condensedItem == null)) {
            player.sendMessage(ColorManager.format("#FF8888 아이템 데이터를 찾을 수 없습니다."));
            return;
        }

        // 65개 이상은 아이템이 유실되는 버그가 있어 50개 단위 스택으로 쪼개서 발송한다.
        List<ItemStack> stacks = new ArrayList<>();
        addStacks(stacks, condensedItem, condensedAmount);
        addStacks(stacks, looseItem, looseAmount);

        // 한 메일에 최대 9스택까지만 담고, 넘치면 여러 통으로 나눠 보낸다.
        for (int i = 0; i < stacks.size(); i += 9) {
            List<ItemStack> mailItems = new ArrayList<>(stacks.subList(i, Math.min(i + 9, stacks.size())));
            Mail mail = MMOMail.getInstance().getMailAPI().createMail(
                    "광물 가방",
                    "광물 가방에서 회수한 아이템입니다.",
                    0,
                    mailItems
            );
            MMOMail.getInstance().getMailAPI().sendMail(player.getName(), mail);
        }

        int remaining = owned - amount;
        if (remaining <= 0) {
            dto.clearCount(itemId);
        } else {
            dto.getBag().put(itemId, remaining);
        }
        MiningBagRepository.getInstance().saveBag(uuid, dto);

        List<String> parts = new ArrayList<>();
        if (condensedAmount > 0) {
            parts.add("응집 " + condensedAmount + "개");
        }
        if (looseAmount > 0) {
            parts.add("낱개 " + looseAmount + "개");
        }
        player.sendMessage(ColorManager.format(
                "#FFF285  [ 광물 가방 ] §f" + displayNameOf(itemId)
                        + "§f §6x" + amount + " §f회수가 완료되어 §6메일함§f에서 수령하실 수 있습니다."
                        + " §7§o(" + String.join(", ", parts) + ", 남은 수량 " + remaining + "개)"
        ));
    }

    private static void addStacks(List<ItemStack> stacks, ItemStack item, int amount) {
        int left = amount;
        while (left > 0) {
            int stackSize = Math.min(50, left);
            ItemStack stack = item.clone();
            stack.setAmount(stackSize);
            stacks.add(stack);
            left -= stackSize;
        }
    }

    public static void withdraw(Player player, String itemId) {
        String uuid = player.getUniqueId().toString();
        MiningBagDto dto = MiningBagRepository.getInstance().getBagCache().get(uuid);
        if (dto == null) {
            player.sendMessage(ColorManager.format("#FF8888 가방 정보를 불러올 수 없습니다."));
            return;
        }
        int owned = dto.getCount(itemId);
        if (owned <= 0) {
            player.sendMessage(ColorManager.format("#FF8888 회수할 광물이 없습니다."));
            return;
        }
        // 개수를 적지 않으면 한 번에 회수할 수 있는 최대치까지만 회수한다.
        withdraw(player, itemId, Math.min(owned, MiningBagItems.MAX_WITHDRAW));
    }

    /**
     * 인벤토리에 있는 가방 광물(낱개와 응집)을 가방에 다시 넣는다.
     * onlyItemId 가 null 이면 모든 광물을, 아니면 해당 광물만 넣는다.
     */
    public static void deposit(Player player, String onlyItemId) {
        String uuid = player.getUniqueId().toString();
        MiningBagDto dto = MiningBagRepository.getInstance().getBagCache().get(uuid);
        // 가방이 로드되지 않은 상태에서 인벤토리를 비우면 광물이 유실되므로 먼저 막는다.
        if (dto == null) {
            player.sendMessage(ColorManager.format("#FF8888 가방 정보를 불러올 수 없습니다."));
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        Map<String, Long> added = new LinkedHashMap<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!"MISCELLANEOUS".equals(MMOItems.getTypeName(item))) {
                continue;
            }
            String id = MMOItems.getID(item);
            String baseId = MiningBagItems.baseIdOf(id);
            if (baseId == null || (onlyItemId != null && !onlyItemId.equals(baseId))) {
                continue;
            }
            long value = (long) item.getAmount() * MiningBagItems.unitOf(id);
            long current = dto.getCount(baseId) + added.getOrDefault(baseId, 0L);
            if (current + value > Integer.MAX_VALUE) {
                continue;
            }
            added.merge(baseId, value, Long::sum);
            inventory.setItem(slot, null);
        }

        if (added.isEmpty()) {
            player.sendMessage(ColorManager.format("#FF8888 인벤토리에 가방에 넣을 수 있는 광물이 없습니다."));
            return;
        }

        player.sendMessage("");
        for (Map.Entry<String, Long> entry : added.entrySet()) {
            int value = entry.getValue().intValue();
            dto.addCount(entry.getKey(), value);
            player.sendMessage(ColorManager.format(
                    "#FBFFBC " + displayNameOf(entry.getKey())
                            + "§f §6x" + NumberUtil.applyComma(value)
                            + " §7§o(보유 " + NumberUtil.applyComma(dto.getCount(entry.getKey())) + "개)"
            ));
        }
        MiningBagRepository.getInstance().saveBag(uuid, dto);
        player.sendMessage("");
        player.sendMessage(ColorManager.format("#FFF285  [ 광물 가방 ] §f인벤토리의 광물이 가방에 보관되었습니다."));
        player.sendMessage("");
    }

    public static String displayNameOf(String itemId) {
        ItemStack item = MMOItems.plugin.getItem(Type.MISCELLANEOUS, itemId);
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return itemId;
    }
}
