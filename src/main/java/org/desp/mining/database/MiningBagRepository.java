package org.desp.mining.database;

import com.binggre.mongolibraryplugin.base.MongoRedisRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.bson.Document;
import org.desp.mining.Mining;
import org.desp.mining.dto.MiningBagDto;

/**
 * 광물 가방 저장소. (MongoLibraryPlugin 이행 - 채광 데이터와 동일 패턴)
 *
 * - 플레이 중: 로컬 캐시(bagCache)에서 읽고 쓴다.
 * - 저장: Redis(실시간 사본)와 Mongo(영구 저장)에 비동기 저장.
 *   순서는 라이브러리 체인이, 종료 시 완료는 라이브러리 drain이 보장한다.
 * - 로드: Redis 우선(다른 서버가 방금 저장한 최신 사본, 저장 중이면 팻말 대기)
 *   → 없으면 Mongo → 없으면 빈 가방.
 * - 낡은 쓰기 거부(enableWriteGuard): 렉으로 밀린 서버의 뒷북 저장 차단.
 */
public class MiningBagRepository extends MongoRedisRepository<String, MiningBagDto> {

    private static MiningBagRepository instance;
    // 메인 스레드와 비동기 자동저장·조인 시 비동기 로드가 동시에 접근하므로 ConcurrentHashMap 을 사용한다
    @Getter
    private final Map<String, MiningBagDto> bagCache = new ConcurrentHashMap<>();

    private MiningBagRepository() {
        // Mongo: 기존 Mining/MiningBag 네임스페이스 그대로 / Redis: "MiningBag" 해시
        super(Mining.getInstance(), "Mining", "MiningBag", "MiningBag", MiningBagDto.class);
        // 기존 수동 last_saved 가드를 라이브러리 표준 가드로 대체 (같은 필드라 기존 데이터와 호환)
        enableWriteGuard();
    }

    public static synchronized MiningBagRepository getInstance() {
        if (instance == null) {
            instance = new MiningBagRepository();
        }
        return instance;
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
     * Redis(최신 사본) → Mongo(영구 저장) 순으로 가방을 읽는다.
     * DB 오류 시에는 null 을 반환해서, 빈 가방이 실제 데이터를 덮어쓰지 않게 한다.
     */
    public MiningBagDto getPlayerBag(String uuid, String user_id) {
        MiningBagDto dto;
        boolean fromRedis = false;
        try {
            dto = get(uuid);
            fromRedis = dto != null;
            if (dto == null) {
                dto = findById(uuid);
            }
        } catch (Exception e) {
            Mining.getInstance().getLogger().severe("[채광] 가방 로드 실패 (" + user_id + "): " + e.getMessage());
            return null;
        }

        if (dto != null) {
            // 롤백 신고가 들어오면 이 로그로 어느 저장소의 몇 초 전 데이터를 채택했는지 역추적한다
            Mining.getInstance().getLogger().info(
                    "[채광] 가방 로드(" + user_id + "): "
                            + (fromRedis ? "Redis" : "Mongo") + " 채택 (" + savedAgeOf(dto) + ")");
            if (!fromRedis) {
                // 다음 서버 이동을 위해 Redis에 실시간 사본을 시드한다.
                putInAsync(dto);
            }
            return dto;
        }
        return MiningBagDto.builder().user_id(user_id).uuid(uuid).bag(new HashMap<>()).build();
    }

    private String savedAgeOf(MiningBagDto dto) {
        if (dto == null) {
            return "없음";
        }
        return ((System.currentTimeMillis() - dto.getLastSaved()) / 1000L) + "초전";
    }

    /** 로드한 가방을 캐시에 등록한다. */
    public void cacheLoadedBag(String uuid, MiningBagDto bag) {
        bagCache.put(uuid, bag);
    }

    // ======================= 저장 =======================

    /** 일반 저장(가방 넣기/판매/회수): Redis + Mongo 비동기 저장. */
    public void saveBag(String uuid, MiningBagDto dto) {
        persistAsync(dto);
    }

    /**
     * 퇴장 저장. putInAsync가 접수 시점에 팻말을 세우므로,
     * 플레이어가 이동한 다음 서버의 로드는 이 저장이 끝날 때까지 기다린다.
     */
    public void saveBagOnQuit(String uuid, MiningBagDto dto) {
        persistAsync(dto);
    }

    /** 자동저장/onDisable 용 저장. 라이브러리 체인이 순서를, drain이 종료 시 완료를 보장한다. */
    public void saveBagBlocking(String uuid, MiningBagDto dto) {
        persistAsync(dto);
    }

    private void persistAsync(MiningBagDto dto) {
        if (dto == null) {
            return;
        }
        // 로그(savedAgeOf)와 Redis 사본의 last_saved 표시용. 실제 가드 판정은 라이브러리 도장이 한다.
        dto.setLastSaved(System.currentTimeMillis());
        putInAsync(dto);   // Redis - 서버 이동 핸드오프용 실시간 사본
        saveAsync(dto);    // Mongo - 영구 저장 (낡은 쓰기 거부 가드 적용)
    }
}
