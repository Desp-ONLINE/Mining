package org.desp.mining.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.desp.IDEPass.api.IDEPassAPI;
import org.desp.IDEPass.dto.IDEPassUserDataDto;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningItemRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.dto.MiningItemDto;
import org.desp.mining.event.MiningEvent;
import org.dople.profess.API.ProfessAPI;
import org.dople.profess.Database.PlayerRepository;
import org.dople.profess.Dto.ProfessDTO;
import org.swlab.etcetera.EtCetera;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static org.desp.mining.utils.MiningUtils.getMaterials;

public class MythicMobsListener implements Listener {

    @EventHandler
    public void onMythicmobDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player player) {
            Random random = new Random();
            if (random.nextInt(0, 200) == 1) {
                MiningRepository.getInstance().reduceFatigue(player);
                player.sendMessage("§e 사냥을 하니 채광에 대한 피로도가 좀 가신 것 같습니다.");
            }
        }
    }

}
