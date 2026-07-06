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
import lombok.Getter;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.desp.mining.Mining;
import org.desp.mining.dto.MiningBagDto;

/**
 * 광물 가방 저장소.
 * - 세션 데이터: Redis (MongoLibraryPlugin 1.0.8+ 이 제공하는 redisClient() 사용)
 * - 영구 저장: MongoDB (기존 config.yml 연결 / MiningBag 컬렉션 / uuid 필드 그대로)
 */
public class MiningBagRepository extends MongoRedisRepository<String, MiningBagDto> {

    private static final long ONLINE_TTL_SECONDS = 60L * 60L * 2L;
    private static final long OFFLINE_TTL_SECONDS = 60L * 10L;

    private static MiningBagRepository instance;
    private final MongoCollection<Document> bagCollection;
    // 메인 스레드와 비동기 자동저장·조인 시 비동기 로드가 동시에 접근하므로 ConcurrentHashMap 을 사용한다
    @Getter
    private final Map<String, MiningBagDto> bagCache = new ConcurrentHashMap<>();
    // 마지막 플러시 이후 변이가 있었던 가방 (주기 플러시 대상)
    private final Set<String> dirtyBags = ConcurrentHashMap.newKeySet();

    private MiningBagRepository() {
        super(Mining.getInstance(), "Mining", "MiningBag", "MiningBag:", MiningBagDto.class);
        this.bagCollection = new DatabaseRegister().getDatabase().getCollection("MiningBag");
    }

    public static synchronized MiningBagRepository getInstance() {
        if (instance == null) {
            instance = new MiningBagRepository();
        }
        return instance;
    }

    // Redis 는 MongoLibraryPlugin 이 관리하는 공용 클라이언트를 쓴다. 없으면 Mongo 단독으로 동작한다.
    private boolean redisAvailable() {
        try {
            return redisClient() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ======================= 직렬화 =======================

    @Override
    public Document toDocument(MiningBagDto dto) {
        Document bagDoc = new Document();
        if (dto.getBag() != null) {
            dto.getBag().forEach(bagDoc::append);
        }
        return new Document("id", dto.getUuid())
                .append("uuid", dto.getUuid())
                .append("user_id", dto.getUser_id())
                .append("bag", bagDoc)
                .append("last_saved", dto.getLastSaved());
    }

    @Override
    public MiningBagDto toEntity(Document doc) {
        Map<String, Integer> bag = new HashMap<>();
        Document bagDoc = (Document) doc.get("bag");
        if (bagDoc != null) {
            for (String itemId : bagDoc.keySet()) {
                bag.put(itemId, ((Number) bagDoc.get(itemId)).intValue());
            }
        }
        return MiningBagDto.builder()
                .uuid(doc.getString("uuid"))
                .user_id(doc.getString("user_id"))
                .bag(bag)
                .lastSaved(doc.get("last_saved") == null ? 0L : ((Number) doc.get("last_saved")).longValue())
                .build();
    }

    // ======================= 로드 =======================

    /**
     * Redis 와 Mongo 를 모두 읽어 lastSaved 가 더 최신인 쪽을 사용한다.
     * DB 오류 시에는 null 을 반환해서, 빈 가방이 실제 데이터를 덮어쓰지 않게 한다.
     */
    public MiningBagDto getPlayerBag(String uuid, String user_id) {
        MiningBagDto redisDto = readRedis(uuid);
        MiningBagDto mongoDto = null;
        try {
            Document doc = bagCollection.find(Filters.eq("uuid", uuid)).first();
            if (doc != null) {
                mongoDto = toEntity(doc);
            }
        } catch (Exception e) {
            Mining.getInstance().getLogger().severe("[채광] 가방 Mongo 로드 실패 (" + user_id + "): " + e.getMessage());
            if (redisDto == null) {
                return null;
            }
        }

        if (redisDto != null && (mongoDto == null || redisDto.getLastSaved() >= mongoDto.getLastSaved())) {
            return redisDto;
        }
        if (mongoDto != null) {
            return mongoDto;
        }
        return MiningBagDto.builder().user_id(user_id).uuid(uuid).bag(new HashMap<>()).build();
    }

    // ======================= 저장 =======================

    /** 일반 저장(가방 넣기/판매/회수): Redis 즉시 반영 + Mongo 비동기 저장. */
    public void saveBag(String uuid, MiningBagDto dto) {
        if (dto == null) {
            return;
        }
        writeRedis(dto, ONLINE_TTL_SECONDS);
        runAsyncSafe(() -> saveMongo(dto));
    }

    /** 퇴장 저장: Redis 에 짧은 TTL 로 남겨 다음 서버가 인계받게 하고, Mongo 에 영구 저장한다. */
    public void saveBagOnQuit(String uuid, MiningBagDto dto) {
        dirtyBags.remove(uuid);
        if (dto == null) {
            return;
        }
        writeRedis(dto, OFFLINE_TTL_SECONDS);
        runAsyncSafe(() -> saveMongo(dto));
    }

    /** 동기 저장: 자동저장(비동기 스레드)과 onDisable 에서 사용한다. */
    public void saveBagBlocking(String uuid, MiningBagDto dto) {
        if (dto == null) {
            return;
        }
        writeRedis(dto, ONLINE_TTL_SECONDS);
        saveMongo(dto);
    }

    /**
     * join 시 로드한 가방을 캐시에 등록하고, 이전 서버의 퇴장 저장이 우리 로드보다 늦게
     * 도착하는 경우를 대비해 몇 초 뒤 Redis 를 재확인한다. (그 사이 로컬 변경이 없었을 때만 채택)
     */
    public void cacheLoadedBag(String uuid, MiningBagDto bag) {
        bagCache.put(uuid, bag);
        if (!redisAvailable()) {
            return;
        }
        String loadedJson = FileManager.toJson(bag);
        try {
            Bukkit.getScheduler().runTaskLaterAsynchronously(Mining.getInstance(), () -> {
                String redisJson = readRedisRaw(uuid);
                if (redisJson == null || redisJson.equals(loadedJson)) {
                    return;
                }
                Bukkit.getScheduler().runTask(Mining.getInstance(), () -> {
                    MiningBagDto current = bagCache.get(uuid);
                    if (current == null) {
                        return; // 이미 퇴장
                    }
                    if (!FileManager.toJson(current).equals(loadedJson)) {
                        return; // 이 서버에서 이미 변경분이 생겼으면 로컬을 우선한다
                    }
                    MiningBagDto adopted = FileManager.toObject(redisJson, MiningBagDto.class);
                    if (adopted == null || adopted.getUuid() == null) {
                        return;
                    }
                    bagCache.put(uuid, adopted);
                    Mining.getInstance().getLogger().info(
                            "[채광] 늦게 도착한 가방 인계 데이터를 채택: " + adopted.getUser_id());
                });
            }, 60L);
        } catch (IllegalStateException | IllegalArgumentException ignored) {
            // 플러그인 비활성화 중이면 보정을 생략한다
        }
    }

    private String readRedisRaw(String uuid) {
        try {
            return redisClient().get(key + uuid);
        } catch (Exception e) {
            return null;
        }
    }

    /** 데이터 변이 직후 호출: 표시만 해두면 주기 플러시가 모아서 Redis 로 보낸다. */
    public void markSessionDirty(String uuid) {
        dirtyBags.add(uuid);
    }

    /** 주기 플러시. 메인 스레드에서 스냅샷을 만들고 전송은 비동기로 넘긴다. */
    public void flushDirtySessions() {
        if (dirtyBags.isEmpty()) {
            return;
        }
        if (!redisAvailable()) {
            dirtyBags.clear();
            return;
        }
        List<String[]> entries = new ArrayList<>();
        for (Iterator<String> it = dirtyBags.iterator(); it.hasNext(); ) {
            String uuid = it.next();
            it.remove();
            MiningBagDto dto = bagCache.get(uuid);
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
                    if (!bagCache.containsKey(entry[0])) {
                        continue;
                    }
                    redisClient().setex(key + entry[0], ONLINE_TTL_SECONDS, entry[1]);
                }
            } catch (Exception e) {
                Mining.getInstance().getLogger().warning(
                        "[채광] 가방 Redis 세션 플러시 실패 (" + entries.size() + "건): " + e.getMessage());
            }
        });
    }

    private void saveMongo(MiningBagDto dto) {
        dto.setLastSaved(System.currentTimeMillis());
        Document document = toDocument(dto);
        Exception lastError = null;
        // 일시적인 네트워크 오류로 영구 저장이 누락되지 않도록 짧은 간격으로 재시도한다.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                bagCollection.replaceOne(Filters.eq("uuid", dto.getUuid()), document,
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
                "[채광] 가방 Mongo 저장 실패 (재시도 소진, " + dto.getUser_id() + "): "
                        + (lastError == null ? "interrupted" : lastError.getMessage()));
    }

    private void runAsyncSafe(Runnable task) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(Mining.getInstance(), task);
        } catch (IllegalStateException | IllegalArgumentException e) {
            task.run();
        }
    }

    // ======================= Redis =======================

    private MiningBagDto readRedis(String uuid) {
        if (!redisAvailable()) {
            return null;
        }
        try {
            return get(uuid);
        } catch (Exception e) {
            Mining.getInstance().getLogger().warning("[채광] 가방 Redis 로드 실패, Mongo 로 대체: " + e.getMessage());
            return null;
        }
    }

    private void writeRedis(MiningBagDto dto, long ttlSeconds) {
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
                    "[채광] 가방 Redis 저장 실패 (" + userIdForLog + "): " + e.getMessage());
        }
    }
}
