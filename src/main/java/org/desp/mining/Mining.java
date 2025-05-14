package org.desp.mining;

import static org.desp.mining.listener.MiningListener.miningCache;

import org.desp.mining.command.FatigueCommand;
import org.desp.mining.listener.MiningListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.listener.MiningListener;
import org.desp.mining.scheduler.MiningScheduler;

public final class Mining extends JavaPlugin {

    @Getter
    private static Mining instance;

    @Override
    public void onEnable() {
        instance = this;
        MiningScheduler.startFatigueReductionTask();
        Bukkit.getPluginManager().registerEvents(new MiningListener(), this);
        getCommand("피로도확인").setExecutor(new FatigueCommand());

        Collection<? extends Player> onlinePlayers = Bukkit.getServer().getOnlinePlayers();
        MiningRepository repository = MiningRepository.getInstance();

        for (Player player : onlinePlayers) {
            String user_id = player.getName();
            String uuid = player.getUniqueId().toString();
            MiningDto playerMiningData = repository.getPlayerMiningData(uuid, user_id);
            miningCache.put(uuid,playerMiningData);
        }
    }

    @Override
    public void onDisable() {
        Collection<? extends Player> onlinePlayers = Bukkit.getServer().getOnlinePlayers();
        MiningRepository repository = MiningRepository.getInstance();
        for (Player player : onlinePlayers) {
            String uuid = player.getUniqueId().toString();
            if (miningCache.get(uuid) == null) {
                MiningDto newPlayer = MiningDto.builder()
                        .user_id(player.getName())
                        .uuid(player.getUniqueId().toString())
                        .fatigue(0)
                        .build();
                miningCache.put(uuid, newPlayer);
            }
            repository.saveMining(uuid, miningCache);
        }
    }
}
