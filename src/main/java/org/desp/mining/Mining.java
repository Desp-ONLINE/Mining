package org.desp.mining;

import java.util.Collection;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.desp.mining.command.FatigueCommand;
import org.desp.mining.command.MiningAdminCommand;
import org.desp.mining.command.MiningBagCommand;
import org.desp.mining.command.MiningEventCommand;
import org.desp.mining.command.MiningSkillCommand;
import org.desp.mining.command.ReduceFatigueCommand;
import org.desp.mining.database.LegacyDataMigration;
import org.desp.mining.database.MiningBagRepository;
import org.desp.mining.database.MiningConfigRepository;
import org.desp.mining.database.MiningItemRepository;
import org.desp.mining.database.MiningRepository;
import org.desp.mining.database.MongoSaveExecutor;
import org.desp.mining.dto.MiningBagDto;
import org.desp.mining.gui.MiningSkillGUIListener;
import org.desp.mining.listener.AfkJoinLoadListener;
import org.desp.mining.listener.DataSyncLoadListener;
import org.desp.mining.listener.MiningListener;
import org.desp.mining.listener.MythicMobsListener;
import org.desp.mining.placeholder.MiningPlaceHolder;
import org.desp.mining.scheduler.MiningScheduler;
import org.swlab.etcetera.EtCetera;

public final class Mining extends JavaPlugin {

    @Getter
    private static Mining instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        try {
            // MLP 1.0.8+ 의 id 유니크 인덱스 생성이 실패하지 않도록, 저장소 초기화 전에
            // 예전 문서에 id 필드를 채우고 중복 문서를 정리한다. (멱등)
            LegacyDataMigration.run();
            // 채광 레벨/스킬 설정을 DB에서 불러온다. (문서가 없으면 기본값 시드)
            MiningConfigRepository.getInstance();
            MiningItemRepository.getInstance();
            MiningRepository.getInstance();
            MiningBagRepository.getInstance();
        } catch (Throwable t) {
            // 저장소 초기화가 실패한 채로 돌면 데이터가 유실되므로 명확히 알리고 내린다.
            getLogger().severe("저장소 초기화 실패로 플러그인을 비활성화합니다: " + t);
            t.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        MiningScheduler.startFatigueReductionTask();
        Bukkit.getPluginManager().registerEvents(new MiningListener(), this);
        // afk 채널에는 DataSync 가 없어 DataLoadEvent 를 쓸 수 없다 → 접속 5초 뒤 로드로 대체.
        // (DataSyncLoadListener 를 afk 에서 등록하면 DataLoadEvent 클래스 로드 실패로 터진다)
        if ("afk".equals(EtCetera.getChannelType())) {
            Bukkit.getPluginManager().registerEvents(new AfkJoinLoadListener(), this);
        } else {
            Bukkit.getPluginManager().registerEvents(new DataSyncLoadListener(), this);
        }
        Bukkit.getPluginManager().registerEvents(new MythicMobsListener(), this);
        Bukkit.getPluginManager().registerEvents(new MiningSkillGUIListener(), this);
        getCommand("피로도확인").setExecutor(new FatigueCommand());
        getCommand("피로도감소").setExecutor(new ReduceFatigueCommand());
        getCommand("광물가방").setExecutor(new MiningBagCommand());
        getCommand("채광스킬").setExecutor(new MiningSkillCommand());
        getCommand("채광관리").setExecutor(new MiningAdminCommand());
        getCommand("채광이벤트").setExecutor(new MiningEventCommand());

        MiningRepository repository = MiningRepository.getInstance();
        MiningBagRepository bagRepository = MiningBagRepository.getInstance();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            repository.loadPlayerData(onlinePlayer);

            String uuid = onlinePlayer.getUniqueId().toString();
            MiningBagDto bag = bagRepository.getPlayerBag(uuid, onlinePlayer.getName());
            if (bag != null) {
                bagRepository.getBagCache().put(uuid, bag);
            }
        }
        runAutoSaveScheduler();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MiningPlaceHolder(this).register();
        }
    }

    @Override
    public void onDisable() {
        // 서버 종료 시에는 킥 → 퇴장 이벤트 → disable 순서라 이 시점엔 보통 온라인 플레이어가 없다.
        // (아래 루프는 /reload 처럼 접속 유지 상태로 내려가는 경우를 위한 것)
        try {
            MiningRepository repository = MiningRepository.getInstance();
            MiningBagRepository bagRepository = MiningBagRepository.getInstance();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                repository.savePlayerDataBlocking(onlinePlayer);

                String uuid = onlinePlayer.getUniqueId().toString();
                MiningBagDto bag = bagRepository.getBagCache().get(uuid);
                if (bag != null) {
                    bagRepository.saveBagBlocking(uuid, bag);
                }
            }
        } catch (Throwable t) {
            // onEnable 에서 저장소 초기화가 실패한 채 내려가는 경우 등
            getLogger().severe("종료 저장 실패: " + t);
        }
        // 퇴장 이벤트에서 제출된 Mongo 저장까지 전부 끝난 뒤에 내려간다.
        // (Bukkit 비동기 스케줄러 시절에는 여기서 전부 취소돼 재시작 때마다 롤백이 났다)
        MongoSaveExecutor.shutdownAndDrain();
    }

    // 5분마다 온라인 플레이어의 채광 데이터(피로도/레벨/경험치/스킬, 가방)를 저장한다.
    // 크래시나 저장 실패 시 롤백 폭을 줄이기 위한 것으로, 한 명이 실패해도 나머지는 계속 저장한다.
    public void runAutoSaveScheduler() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            MiningRepository repository = MiningRepository.getInstance();
            MiningBagRepository bagRepository = MiningBagRepository.getInstance();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                try {
                    repository.savePlayerDataBlocking(onlinePlayer);

                    String uuid = onlinePlayer.getUniqueId().toString();
                    MiningBagDto bag = bagRepository.getBagCache().get(uuid);
                    if (bag != null) {
                        bagRepository.saveBagBlocking(uuid, bag);
                    }
                } catch (Exception e) {
                    getLogger().warning("자동 저장 실패 (" + onlinePlayer.getName() + "): " + e.getMessage());
                }
            }
        }, 6000L, 6000L);
    }
}
