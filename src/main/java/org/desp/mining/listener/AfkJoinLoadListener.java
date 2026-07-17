package org.desp.mining.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.desp.mining.Mining;

/**
 * afk 채널에는 DataSync 가 없어 DataLoadEvent 가 오지 않으므로,
 * 접속 5초 뒤에 채광 데이터를 로드한다. (이전 서버의 퇴장 저장이 끝날 시간을 벌기 위한 지연)
 */
public class AfkJoinLoadListener implements Listener {

    private static final long LOAD_DELAY_TICKS = 100L; // 5초

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(Mining.getInstance(), () -> {
            if (player.isOnline()) {
                PlayerDataLoader.loadAsync(player);
            }
        }, LOAD_DELAY_TICKS);
    }
}
