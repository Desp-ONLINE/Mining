package org.desp.mining.database;

import com.binggre.mongolibraryplugin.base.MongoRedisRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.desp.mining.Mining;
import org.desp.mining.dto.MiningDto;

/**
 * 채광 플레이어 데이터 저장소. (MongoLibraryPlugin 이행 파일럿)
 *
 * - 플레이 중: 로컬 캐시(playerCache)에서 읽고 쓴다.
 * - 자동 저장/퇴장: Redis(실시간 사본)와 Mongo(영구 저장)에 비동기 저장.
 *   같은 플레이어의 저장 순서는 라이브러리의 키별 체인이 보장하고,
 *   서버 종료 시에는 라이브러리 drain이 완료를 보장한다.
 * - 접속 로드: Redis 우선(다른 서버가 방금 저장한 최신 사본, 저장 중이면 팻말 대기)
 *   → 없으면 Mongo → 없으면 신규 생성.
 * - 낡은 쓰기 거부(enableWriteGuard): 렉으로 밀린 서버의 뒷북 저장이
 *   최신 데이터를 되돌리지 못하게 저장소 수준에서 차단한다.
 */
public class MiningRepository extends MongoRedisRepository<String, MiningDto> {

    private static MiningRepository instance;
    private static final Map<String, MiningDto> playerCache = new ConcurrentHashMap<>();

    private MiningRepository() {
        // Mongo: 기존 Mining/Mining 네임스페이스 그대로 (라이브러리 클라이언트 사용)
        // Redis: "Mining" 해시 (필드 = uuid, 값 = json)
        super(Mining.getInstance(), "Mining", "Mining", "Mining", MiningDto.class);
        // 기존 수동 last_saved 가드를 라이브러리 표준 가드로 대체 (같은 필드라 기존 데이터와 호환)
        enableWriteGuard();
    }

    public static synchronized MiningRepository getInstance() {
        if (instance == null) {
            instance = new MiningRepository();
        }
        return instance;
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
     * Redis(최신 사본) → Mongo(영구 저장) 순으로 읽어 로컬 캐시에 올린다.
     * 다른 서버가 이 플레이어를 저장 중이면 라이브러리가 팻말을 보고 잠깐 기다린 뒤 읽는다.
     * DB 오류와 "데이터 없음"을 구분해서, 오류일 때는 절대 기본값을 만들지 않는다.
     * (기본값으로 채우면 실데이터를 빈 데이터로 덮어쓰게 된다)
     */
    public void loadPlayerData(Player player) {
        String uuid = player.getUniqueId().toString();

        MiningDto dto;
        boolean fromRedis = false;
        try {
            dto = get(uuid);
            fromRedis = dto != null;
            if (dto == null) {
                dto = findById(uuid);
            }
        } catch (Exception e) {
            Mining.getInstance().getLogger().severe(
                    "[채광] 데이터 로드 실패 (" + player.getName() + "): " + e.getMessage());
            // 못 읽으면 기본값을 만들지 않고 포기한다. 캐시가 비어 있으면 채광이 차단된다.
            return;
        }

        if (dto == null) {
            dto = createNewPlayerData(player);
        } else {
            // 롤백 신고가 들어오면 이 로그로 어느 저장소의 몇 초 전 데이터를 채택했는지 역추적한다
            Mining.getInstance().getLogger().info(
                    "[채광] 로드(" + player.getName() + "): "
                            + (fromRedis ? "Redis" : "Mongo") + " 채택 (" + savedAgeOf(dto) + ")");
        }

        dto.setUser_id(player.getName());
        playerCache.put(uuid, dto);

        if (!fromRedis) {
            // 다음 서버 이동을 위해 Redis에 실시간 사본을 시드한다.
            putInAsync(dto);
        }
    }

    private String savedAgeOf(MiningDto dto) {
        if (dto == null) {
            return "없음";
        }
        return ((System.currentTimeMillis() - dto.getLastSaved()) / 1000L) + "초전";
    }

    /**
     * 신규 데이터 생성. 같은 닉네임의 다른 uuid 문서가 있으면 초기화 사고(UUID 변경)일 가능성이
     * 높으므로 크게 로그를 남긴다. (프록시 포워딩/온라인 모드 설정 문제 추적용)
     */
    private MiningDto createNewPlayerData(Player player) {
        try {
            MiningDto byUserId = findByFilter("user_id", player.getName());
            if (byUserId != null) {
                Mining.getInstance().getLogger().severe(
                        "[채광] UUID 불일치 감지! user_id=" + player.getName()
                                + " 기존 uuid=" + byUserId.getUuid()
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
        persistAsync(dto);
        return dto;
    }

    // ======================= 저장 =======================

    /** 일반 저장: Redis + Mongo 비동기 저장. */
    public void savePlayerData(Player player) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setUser_id(player.getName());
        persistAsync(dto);
    }

    /**
     * 퇴장 저장: Redis(실시간 사본)와 Mongo(영구)에 저장한 뒤 로컬 캐시를 비운다.
     * putInAsync가 접수 시점에 팻말을 세우므로, 플레이어가 이동한 다음 서버의 로드는
     * 이 저장이 끝날 때까지 기다렸다가 최신 데이터를 읽는다.
     */
    public void savePlayerDataOnQuit(Player player) {
        MiningDto dto = playerCache.remove(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setUser_id(player.getName());
        persistAsync(dto);
    }

    /**
     * 자동저장/onDisable 용 저장. 라이브러리 체인이 순서를, 라이브러리 drain이
     * 종료 시 완료를 보장한다.
     */
    public void savePlayerDataBlocking(Player player) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        persistAsync(dto);
    }

    private void persistAsync(MiningDto dto) {
        // 로그(savedAgeOf)와 Redis 사본의 last_saved 표시용. 실제 가드 판정은 라이브러리 도장이 한다.
        dto.setLastSaved(System.currentTimeMillis());
        putInAsync(dto);   // Redis - 서버 이동 핸드오프용 실시간 사본
        saveAsync(dto);    // Mongo - 영구 저장 (낡은 쓰기 거부 가드 적용)
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
        return findByFilter("user_id", user_id) != null;
    }

    /** user_id 로 이 서버에 접속 중인 플레이어의 세션 데이터를 찾는다. (오프라인이면 null) */
    public MiningDto getPlayer(String user_id) {
        MiningDto find = findByFilter("user_id", user_id);
        if (find != null) {
            return playerCache.get(find.getUuid());
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
    }

    public void addFatigue(Player player, double amount) {
        MiningDto dto = playerCache.get(player.getUniqueId().toString());
        if (dto == null) {
            return;
        }
        dto.setFatigue(Math.min(roundFatigue(dto.getFatigue() + amount), 100.0));
    }

    // 0.6 같은 값은 2진 부동소수점으로 정확히 표현되지 않아 누적 시 99.99999999... 가 되므로 항상 소수 둘째 자리로 반올림해 저장한다
    private double roundFatigue(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
