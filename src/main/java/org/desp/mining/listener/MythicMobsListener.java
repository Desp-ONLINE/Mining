package org.desp.mining.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import java.util.Random;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.desp.mining.database.MiningRepository;

public class MythicMobsListener implements Listener {

    @EventHandler
    public void onMythicmobDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player player) {
            Random random = new Random();
            if (random.nextInt(0, 200) == 1) {
                // 저장소가 캐시 변이와 dirty 표시(주기 플러시 대상)를 함께 처리한다.
                MiningRepository.getInstance().reduceFatigue(player);
                player.sendMessage("§e 사냥을 하니 채광에 대한 피로도가 좀 가신 것 같습니다.");
            }
        }
    }

}
