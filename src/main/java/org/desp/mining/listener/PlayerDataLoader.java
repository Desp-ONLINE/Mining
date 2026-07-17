package org.desp.mining.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.desp.mining.Mining;
import org.desp.mining.database.MiningBagRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.dto.MiningBagDto;

/**
 * 채광 데이터/광물가방 로드 공용 로직.
 * 일반 채널은 DataSync 의 DataLoadEvent 시점({@link DataSyncLoadListener}),
 * afk 채널은 DataSync 가 없어 접속 5초 뒤({@link AfkJoinLoadListener})에 호출된다.
 */
public final class PlayerDataLoader {

    private PlayerDataLoader() {
    }

    public static void loadAsync(Player player) {
        String user_id = player.getName();
        String uuid = player.getUniqueId().toString();

        // Mongo I/O 를 메인 스레드에서 하지 않도록 비동기로 로드한다.
        Bukkit.getScheduler().runTaskAsynchronously(Mining.getInstance(), () -> {
            if (!player.isOnline()) {
                return; // 로드 전에 퇴장했으면 캐시를 만들지 않는다
            }
            MiningRepository.getInstance().loadPlayerData(player);
            if (!player.isOnline()) {
                // 로드 도중 퇴장: 퇴장 저장은 이미 지나갔으므로 캐시만 정리한다 (변이 전이라 유실 없음)
                MiningRepository.getInstance().removePlayerData(player);
                return;
            }

            MiningBagDto bag = MiningBagRepository.getInstance().getPlayerBag(uuid, user_id);
            // 로드 실패(null)이거나 로드 중 퇴장했으면 캐시에 넣지 않는다. (빈 가방 덮어쓰기 방지)
            if (bag != null && player.isOnline()) {
                MiningBagRepository.getInstance().cacheLoadedBag(uuid, bag);
            }
        });
    }
}
