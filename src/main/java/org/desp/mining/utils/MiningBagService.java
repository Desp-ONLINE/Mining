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
import org.desp.mining.database.MiningBagRepository;
import org.desp.mining.dto.MiningBagDto;

import java.util.ArrayList;
import java.util.List;

public class MiningBagService {

    public static void showBag(Player player) {
        String uuid = player.getUniqueId().toString();
        MiningBagDto dto = MiningBagRepository.getInstance().getBagCache().get(uuid);

        player.sendMessage("");
        player.sendMessage(ColorManager.format("#FFF285  [ 광물 가방 ]"));
        player.sendMessage("");
        for (String itemId : MiningBagItems.getItemIds()) {
            int count = dto == null ? 0 : dto.getCount(itemId);
            player.sendMessage(ColorManager.format(
                    "#FBFFBC " + displayNameOf(itemId)
                            + " §7§o( §f" + NumberUtil.applyComma(count) + "§7§o개 보유중 )"
            ));
        }
        player.sendMessage("");
        player.sendMessage(ColorManager.format("#AAAAAA /광물가방 판매 #7B7B7B- #FFEA80가방에 있는 모든 광물을 전체 판매합니다."));
        player.sendMessage(ColorManager.format("#AAAAAA /광물가방 회수 [철/금/청금석/다이아몬드/에메랄드] (개수) #7B7B7B- #FFEA80가방에 있는 광물을 메일함으로 회수 합니다."));
        player.sendMessage("");
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
        for (String itemId : MiningBagItems.getItemIds()) {
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
        if (amount > owned) {
            player.sendMessage(ColorManager.format(
                    "#FF8888 보유 수량보다 많습니다. §7§o(보유: " + owned + "개)"
            ));
            return;
        }

        ItemStack item = MMOItems.plugin.getItem(Type.MISCELLANEOUS, itemId);
        if (item == null) {
            player.sendMessage(ColorManager.format("#FF8888 아이템 데이터를 찾을 수 없습니다."));
            return;
        }

        // 65개 이상은 아이템이 유실되는 버그가 있어 50개 단위 스택으로 쪼개서 발송한다.
        List<ItemStack> stacks = new ArrayList<>();
        if (amount <= 64) {
            ItemStack stack = item.clone();
            stack.setAmount(amount);
            stacks.add(stack);
        } else {
            int left = amount;
            while (left > 0) {
                int stackSize = Math.min(50, left);
                ItemStack stack = item.clone();
                stack.setAmount(stackSize);
                stacks.add(stack);
                left -= stackSize;
            }
        }

        // 한 메일에 최대 9스택(450개)까지만 담고, 넘치면 여러 통으로 나눠 보낸다.
        int mailCount = 0;
        for (int i = 0; i < stacks.size(); i += 9) {
            List<ItemStack> mailItems = new ArrayList<>(stacks.subList(i, Math.min(i + 9, stacks.size())));
            Mail mail = MMOMail.getInstance().getMailAPI().createMail(
                    "광물 가방",
                    "광물 가방에서 회수한 아이템입니다.",
                    0,
                    mailItems
            );
            MMOMail.getInstance().getMailAPI().sendMail(player.getName(), mail);
            mailCount++;
        }

        int remaining = owned - amount;
        if (remaining <= 0) {
            dto.clearCount(itemId);
        } else {
            dto.getBag().put(itemId, remaining);
        }
        MiningBagRepository.getInstance().saveBag(uuid, dto);

        String splitInfo = stacks.size() > 1
                ? " §7§o(50개씩 " + stacks.size() + "묶음, 메일 " + mailCount + "통)"
                : "";
        player.sendMessage(ColorManager.format(
                "#FFF285  [ 광물 가방 ] §f" + displayNameOf(itemId)
                        + "§f §6x" + amount + " §f을(를) §6메일§f로 발송하였습니다."
                        + splitInfo
                        + " §7§o(남은 수량: " + remaining + "개)"
        ));
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
        withdraw(player, itemId, owned);
    }

    public static String displayNameOf(String itemId) {
        ItemStack item = MMOItems.plugin.getItem(Type.MISCELLANEOUS, itemId);
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return itemId;
    }
}
