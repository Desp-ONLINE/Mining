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
import org.desp.mining.dto.MiningBagDto;
import org.desp.mining.gui.MiningSkillGUIListener;
import org.desp.mining.listener.MiningListener;
import org.desp.mining.listener.MythicMobsListener;
import org.desp.mining.placeholder.MiningPlaceHolder;
import org.desp.mining.scheduler.MiningScheduler;

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
        runSessionFlushScheduler();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MiningPlaceHolder(this).register();
        }
    }

    @Override
    public void onDisable() {
        // 스케줄러를 못 쓰는 시점이므로 동기 저장을 사용한다.
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

    // 5초마다 변이가 있었던 세션(dirty)만 Redis 로 전송한다.
    // 메인 스레드에서 스냅샷(직렬화)만 하고 네트워크 전송은 비동기라, 유저 수가 늘어도
    // Redis 쓰기는 활동 유저당 5초에 1회로 고정된다. (서버 이동 인계 최신성의 상한 = 5초)
    public void runSessionFlushScheduler() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            MiningRepository.getInstance().flushDirtySessions();
            MiningBagRepository.getInstance().flushDirtySessions();
        }, 100L, 100L);
    }
}
