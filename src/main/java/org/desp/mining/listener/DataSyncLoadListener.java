package org.desp.mining.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.dople.dataSync.event.DataLoadEvent;

/**
 * DataSync 가 인벤토리 등 동기화 데이터를 다 불러온 시점(DataLoadEvent)에 채광 데이터를 로드한다.
 * 로드 전에는 캐시가 비어 있어 채광이 차단된다. (MiningListener.onBlockBreak 의 로드 확인 참고)
 *
 * 주의: DataSync 가 없는 채널(afk)에서 이 클래스를 등록하면 DataLoadEvent 클래스 로드가 실패하므로,
 * 반드시 채널 확인 후 등록해야 한다. (Mining.onEnable 참고 — afk 는 AfkJoinLoadListener 사용)
 */
public class DataSyncLoadListener implements Listener {

    @EventHandler
    public void onDataLoad(DataLoadEvent event) {
        PlayerDataLoader.loadAsync(event.getPlayer());
    }
}
