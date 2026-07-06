package org.desp.mining.database;

import com.binggre.binggreapi.utils.file.FileManager;
import com.binggre.mongolibraryplugin.base.MongoRedisRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.desp.mining.Mining;
import org.desp.mining.dto.MiningDto;

/**
 * 채광 플레이어 데이터 저장소.
 * - 세션 데이터: Redis (MongoLibraryPlugin 1.0.8+ 이 제공하는 redisClient() 사용)
 * - 영구 저장: MongoDB (기존 config.yml 연결 / Mining 컬렉션 / uuid 필드 그대로)
 * 멀티서버에서 서버 이동 시 Redis 를 먼저 읽어 다른 서버의 최신 진행분을 인계받는다.
 */
public class MiningRepository extends MongoRedisRepository<String, MiningDto> {

    // 세션 중 Redis 키 유지 시간. 채광/자동저장(5분) 때마다 갱신되므로 접속 중에는 만료되지 않는다.
    private static final long ONLINE_TTL_SECONDS = 60L * 60L * 2L;
    // 퇴장 후 유지 시간. 서버 이동 인계용으로만 짧게 유지한다.
    // (피로도 감소가 오프라인에는 반영되지 않으므로 퇴장 키를 오래 남기면 옛 피로도가 되살아난다)
    private static final long OFFLINE_TTL_SECONDS = 60L * 10L;

    private static MiningRepository instance;
    private final MongoCollection<Document> miningCollection;
    private static final Map<String, MiningDto> playerCache = new ConcurrentHashMap<>();
    // 마지막 플러시 이후 변이가 있었던 유저 (주기 플러시 대상)
    private static final Set<String> dirtyPlayers = ConcurrentHashMap.newKeySet();

    private MiningRepository() {
        // 부모 클래스는 Redis(get/remove)와 직렬화 규격 용도로 사용하고,
        // Mongo 영구 저장은 기존 config.yml 연결을 그대로 사용해 기존 데이터와의 호환을 보장한다.
        super(Mining.getInstance(), "Mining", "Mining", "MiningPlayer:", MiningDto.class);
        this.miningCollection = new DatabaseRegister().getDatabase().getCollection("Mining");
    }

    public static synchronized MiningRepository getInstance() {
        if (instance == null) {
            instance = new MiningRepository();
        }
        return instance;
    }

    // Redis 는 MongoLibraryPlugin 이 관리하는 공용 클라이언트를 쓴다. 없으면 Mongo 단독으로 동작한다.
    private static boolean redisWarned = false;

    private boolean redisAvailable() {
        try {
            if (redisClient() != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (!redisWarned) {
            redisWarned = true;
            Mining.getInstance().getLogger().warning(
                    "[채광] MongoLibraryPlugin 의 Redis 클라이언트를 사용할 수 없어 Mongo 단독으로 동작합니다. (멀티서버 인계 비활성)");
        }
        return false;
    }

    // ======================= 직렬화 =======================

    @Override
    public Document toDocument(MiningDto dto) {
        Document skillsDoc = new Document();
        if (dto.getSkills() != null) {
            dto.getSkills().forEach(skillsDoc::append);
        }
        return new Document("id", dto.getUuid())
                .append("uuid", dto.getUuid())
                .append("user_id", dto.getUser_id())
                .append("fatigue", dto.getFatigue())
                .append("level", dto.getLevel())
                .append("exp", dto.getExp())
                .append("skill_points", dto.getSkillPoints())
                .append("skills", skillsDoc)
                .append("last_saved", dto.getLastSaved());
    }

    @Override
    public MiningDto toEntity(Document doc) {
        double fatigue = doc.get("fatigue") == null ? 0.0 : ((Number) doc.get("fatigue")).doubleValue();
        int level = doc.get("level") == null ? 1 : ((Number) doc.get("level")).intValue();
        double exp = doc.get("exp") == null ? 0 : ((Number) doc.get("exp")).doubleValue();
        int skillPoints = doc.get("skill_points") == null ? 0 : ((Number) doc.get("skill_points")).intValue();

        Map<String, Integer> skills = new HashMap<>();
        Document skillsDoc = (Document) doc.get("skills");
        if (skillsDoc != null) {
            for (String skillId : skillsDoc.keySet()) {
                skills.put(skillId, ((Number) skillsDoc.get(skillId)).intValue());
            }
        }

        return MiningDto.builder()
                .uuid(doc.getString("uuid"))
                .user_id(doc.getString("user_id"))
                .fatigue(roundFatigue(fatigue))
                .level(Math.max(level, 1))
                .exp(exp)
                .skillPoints(skillPoints)
                .skills(skills)
                .lastSaved(doc.get("last_saved") == null ? 0L : ((Number) doc.get("last_saved")).longValue())
                .build();
    }

    // ======================= 로드 =======================

    /**
     * Redis 와 Mongo 를 모두 읽어 lastSaved 가 더 최신인 쪽을 사용한다.
     * (플러시/자동저장의 낡은 Redis 사본이 퇴장 저장본을 늦게 덮어써도 Mongo 쪽이 최신이면 그걸 택한다)
     * DB 오류와 "데이터 없음"을 구분해서, 오류일 때는 절대 기본값을 만들지 않는다.
     */
    public void loadPlayerData(Player player) {
        String uuid = player.getUniqueId().toString();

        MiningDto redisDto = readRedis(uuid);
        MiningDto mongoDto = null;
        try {
            Document doc = miningCollection.find(Filters.eq("uuid", uuid)).first();
            if (doc != null) {
                mongoDto = toEntity(doc);
            }
        } catch (Exception e) {
            Mining.getInstance().getLogger().severe(
                    "[채광] Mongo 로드 실패 (" + player.getName() + "): " + e.getMessage());
            if (redisDto == null) {
                // 양쪽 다 못 읽으면 기본값을 만들지 않고 포기한다. (실데이터 덮어쓰기 방지)
                return;
            }
        }

        MiningDto dto;
        boolean fromRedis;
        if (redisDto != null && (mongoDto == null || redisDto.getLastSaved() >= mongoDto.getLastSaved())) {
            dto = redisDto;
            fromRedis = true;
        } else {
            dto = mongoDto;
            fromRedis = false;
        }

        if (dto == null) {
            dto = createNewPlayerData(player);
        }

        dto.setUser_id(player.getName());
        playerCache.put(uuid, dto);
        if (fromRedis) {
            // 인계본을 그대로 다시 쓰면 이전 서버의 "늦게 도착한 퇴장 저장"을 덮을 수 있으므로 TTL 만 갱신한다.
            touchRedisTtl(uuid);
        } else {
            writeRedis(dto, ONLINE_TTL_SECONDS);
        }
        scheduleHandoffRecheck(player, FileManager.toJson(dto));
    }

    /**
     * 서버 이동 시 이전 서버의 퇴장 저장이 우리 로드보다 늦게 도착하는 경우 보정한다.
     * 로드 몇 초 뒤 Redis 를 다시 읽어, 값이 바뀌었고 그 사이 로컬 진행이 없었다면 인계본을 채택한다.
     */
    private void scheduleHandoffRecheck(Player player, String loadedJson) {
        if (!redisAvailable()) {
            return;
        }
        String uuid = player.getUniqueId().toString();
        try {
            Bukkit.getScheduler().runTaskLaterAsynchronously(Mining.getInstance(), () -> {
                String redisJson = readRedisRaw(uuid);
                if (redisJson == null || redisJson.equals(loadedJson)) {
                    return;
                }
                Bukkit.getScheduler().runTask(Mining.getInstance(), () -> {
                    MiningDto current = playerCache.get(uuid);
                    if (current == null) {
                        return; // 이미 퇴장
                    }
                    if (!FileManager.toJson(current).equals(loadedJson)) {
                        return; // 이 서버에서 이미 진행분이 생겼으면 로컬을 우선한다
                    }
                    MiningDto adopted = FileManager.toObject(redisJson, MiningDto.class);
                    if (adopted == null || adopted.getUuid() == null) {
                        return;
                    }
                    adopted.setUser_id(player.getName());
                    playerCache.put(uuid, adopted);
                    touchRedisTtl(uuid);
                    Mining.getInstance().getLogger().info(
                            "[채광] 늦게 도착한 서버 인계 데이터를 채택: " + player.getName());
                });
            }, 60L);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 플러그인 비활성화 중이면 보정을 생략한다
        }
    }

    /**
     * 신규 데이터 생성. 같은 닉네임의 다른 uuid 문서가 있으면 초기화 사고(UUID 변경)일 가능성이
     * 높으므로 크게 로그를 남긴다. (프록시 포워딩/온라인 모드 설정 문제 추적용)
     */
    private MiningDto createNewPlayerData(Player player) {
        try {
            Document byUserId = miningCollection.find(Filters.eq("user_id", player.getName())).first();
            if (byUserId != null) {
                Mining.getInstance().getLogger().severe(
                        "[채광] UUID 불일치 감지! user_id=" + player.getName()
                                + " 기존 uuid=" + byUserId.getString("uuid")
                                + " 현재 uuid=" + player.getUniqueId()
                                + " — 프록시 포워딩/온라인 모드 설정을 확인하세요. 기존 데이터는 보존됩니다.");
            } else {
                Mining.getInstance().getLogger().info("[채광] 신규 채광 데이터 생성: " + player.getName());
            }
        } catch (Exception ignored) {
        }

        MiningDto dto = MiningDto.builder()
                .uuid(player.getUniqueId().toString())
                .user_id(player.getName())
                .fatigue(0.0)
                .build();
        saveMongoAsync(dto);
        return dto;
    }

    // ======================= 저장 =======================

    /** 일반 저장: Redis 즉시 반영 + Mongo 비동기 저장. */
    public void savePlayerData(Player player) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setUser_id(player.getName());
        writeRedis(dto, ONLINE_TTL_SECONDS);
        saveMongoAsync(dto);
    }

    /**
     * 퇴장 저장: Redis 에 짧은 TTL 로 남겨 다음 서버가 인계받게 하고, Mongo 에 영구 저장한 뒤
     * 로컬 캐시를 비운다. (캐시를 남겨두면 재접속 때 다른 서버의 진행분 대신 옛 데이터를 쓰게 된다)
     */
    public void savePlayerDataOnQuit(Player player) {
        dirtyPlayers.remove(player.getUniqueId().toString());
        MiningDto dto = playerCache.remove(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setUser_id(player.getName());
        writeRedis(dto, OFFLINE_TTL_SECONDS);
        saveMongoAsync(dto);
    }

    /** 동기 저장: 자동저장(비동기 스레드)과 onDisable 에서 사용한다. */
    public void savePlayerDataBlocking(Player player) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        writeRedis(dto, ONLINE_TTL_SECONDS);
        saveMongo(dto);
    }

    /**
     * 데이터 변이 직후 호출: 즉시 전송하지 않고 표시만 해두면, 주기 플러시가 모아서 Redis 로 보낸다.
     * (유저가 많아도 Redis 쓰기가 유저당 플러시 주기 1회로 고정된다)
     */
    public void markSessionDirty(Player player) {
        dirtyPlayers.add(player.getUniqueId().toString());
    }

    public void markSessionDirty(String uuid) {
        dirtyPlayers.add(uuid);
    }

    /**
     * 주기 플러시. 메인 스레드에서 호출되어 변이가 있었던 세션만 직렬화(스냅샷)하고,
     * 네트워크 전송은 비동기 스레드에 넘긴다.
     */
    public void flushDirtySessions() {
        if (dirtyPlayers.isEmpty()) {
            return;
        }
        if (!redisAvailable()) {
            dirtyPlayers.clear();
            return;
        }
        List<String[]> entries = new ArrayList<>();
        for (Iterator<String> it = dirtyPlayers.iterator(); it.hasNext(); ) {
            String uuid = it.next();
            it.remove();
            MiningDto dto = playerCache.get(uuid);
            if (dto == null) {
                continue; // 이미 퇴장 (퇴장 저장이 최종본을 이미 기록함)
            }
            dto.setLastSaved(System.currentTimeMillis());
            entries.add(new String[]{uuid, FileManager.toJson(dto)});
        }
        if (entries.isEmpty()) {
            return;
        }
        runAsyncSafe(() -> {
            try {
                for (String[] entry : entries) {
                    // 전송 직전에 퇴장 여부를 다시 확인해서, 퇴장 저장본을 낡은 스냅샷으로 덮는 창을 줄인다
                    if (!playerCache.containsKey(entry[0])) {
                        continue;
                    }
                    redisClient().setex(key + entry[0], ONLINE_TTL_SECONDS, entry[1]);
                }
            } catch (Exception e) {
                Mining.getInstance().getLogger().warning(
                        "[채광] Redis 세션 플러시 실패 (" + entries.size() + "건): " + e.getMessage());
            }
        });
    }

    private void saveMongo(MiningDto dto) {
        dto.setLastSaved(System.currentTimeMillis());
        Document document = toDocument(dto);
        Exception lastError = null;
        // 일시적인 네트워크 오류로 영구 저장이 누락되지 않도록 짧은 간격으로 재시도한다.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                miningCollection.replaceOne(Filters.eq("uuid", dto.getUuid()), document,
                        new ReplaceOptions().upsert(true));
                return;
            } catch (Exception e) {
                lastError = e;
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Mining.getInstance().getLogger().severe(
                "[채광] Mongo 저장 실패 (재시도 소진, " + dto.getUser_id() + "): "
                        + (lastError == null ? "interrupted" : lastError.getMessage()));
    }

    private void saveMongoAsync(MiningDto dto) {
        runAsyncSafe(() -> saveMongo(dto));
    }

    private void runAsyncSafe(Runnable task) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(Mining.getInstance(), task);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 플러그인 비활성화 중에는 스케줄러를 쓸 수 없으므로 그 자리에서 실행한다.
            task.run();
        }
    }

    // ======================= Redis =======================

    private MiningDto readRedis(String uuid) {
        if (!redisAvailable()) {
            return null;
        }
        try {
            return get(uuid);
        } catch (Exception e) {
            Mining.getInstance().getLogger().warning("[채광] Redis 로드 실패, Mongo 로 대체: " + e.getMessage());
            return null;
        }
    }

    private void writeRedis(MiningDto dto, long ttlSeconds) {
        if (!redisAvailable()) {
            return;
        }
        dto.setLastSaved(System.currentTimeMillis());
        setexRaw(key + dto.getUuid(), ttlSeconds, FileManager.toJson(dto), dto.getUser_id());
    }

    private void setexRaw(String redisKey, long ttlSeconds, String json, String userIdForLog) {
        try {
            // RedisClient(UnifiedJedis)는 자체 풀을 관리하므로 close 하면 안 된다
            redisClient().setex(redisKey, ttlSeconds, json);
        } catch (Exception e) {
            Mining.getInstance().getLogger().warning(
                    "[채광] Redis 저장 실패 (" + userIdForLog + "): " + e.getMessage());
        }
    }

    /** 값은 건드리지 않고 TTL 만 세션 유지 시간으로 연장한다. */
    private void touchRedisTtl(String uuid) {
        if (!redisAvailable()) {
            return;
        }
        try {
            redisClient().expire(key + uuid, ONLINE_TTL_SECONDS);
        } catch (Exception e) {
            Mining.getInstance().getLogger().warning("[채광] Redis TTL 갱신 실패: " + e.getMessage());
        }
    }

    private String readRedisRaw(String uuid) {
        try {
            return redisClient().get(key + uuid);
        } catch (Exception e) {
            return null;
        }
    }

    // ======================= 캐시/피로도 =======================

    public MiningDto getPlayerData(Player player) {
        return playerCache.get(player.getUniqueId().toString());
    }

    public MiningDto getPlayerData(String uuid) {
        return playerCache.get(uuid);
    }

    public void removePlayerData(Player player) {
        playerCache.remove(player.getUniqueId().toString());
    }

    public boolean exitsPlayer(String user_id) {
        Document query = miningCollection.find(Filters.eq("user_id", user_id)).first();
        return query != null;
    }

    /** user_id 로 이 서버에 접속 중인 플레이어의 세션 데이터를 찾는다. (오프라인이면 null) */
    public MiningDto getPlayer(String user_id) {
        Document query = miningCollection.find(Filters.eq("user_id", user_id)).first();
        if (query != null) {
            String uuid = query.getString("uuid");
            return playerCache.get(uuid);
        }
        return null;
    }

    /** 피로도 자연 감소 스케줄러용: 캐시된 전원 피로도 -1. */
    public void reduceFatigue() {
        for (Map.Entry<String, MiningDto> entry : playerCache.entrySet()) {
            MiningDto dto = entry.getValue();
            double oldFatigue = dto.getFatigue();
            if (oldFatigue <= 0) {
                continue;
            }
            double newFatigue = Math.max(roundFatigue(oldFatigue - 1), 0.0);
            dto.setFatigue(newFatigue);
            dirtyPlayers.add(entry.getKey());

            if (oldFatigue >= 1 && newFatigue < 1) {
                Player player = Bukkit.getPlayer(dto.getUser_id());
                if (player == null) {
                    continue;
                }
                player.sendMessage("");
                player.sendMessage("§7[채광 알림] §f채광하느라 지친 §c피로§f가 싹~ 가라 앉은 것 같습니다.");
                player.sendMessage("");
                player.playSound(player, "minecraft:block.stem.break", SoundCategory.AMBIENT, 10, 2);
            }
        }
    }

    public void reduceFatigue(Player player) {
        reduceFatigue(player, 1.0);
    }

    public void reduceFatigue(Player player, double amount) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setFatigue(Math.max(roundFatigue(dto.getFatigue() - amount), 0.0));
        markSessionDirty(player);
    }

    public void addFatigue(Player player, double amount) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setFatigue(Math.min(roundFatigue(dto.getFatigue() + amount), 100.0));
        markSessionDirty(player);
    }

    // 0.6 같은 값은 2진 부동소수점으로 정확히 표현되지 않아 누적 시 99.99999999... 가 되므로 항상 소수 둘째 자리로 반올림해 저장한다
    private double roundFatigue(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
