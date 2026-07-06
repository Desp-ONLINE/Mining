package org.desp.mining.dto;

import com.binggre.mongolibraryplugin.base.MongoData;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningDto implements MongoData<String> {
    private String user_id;
    private String uuid;
    private double fatigue;
    @Builder.Default
    private int level = 1;
    @Builder.Default
    private double exp = 0;
    @Builder.Default
    private int skillPoints = 0;
    @Builder.Default
    private Map<String, Integer> skills = new HashMap<>();
    // 마지막 저장 시각(ms). 로드 시 Redis/Mongo 중 더 최신 데이터를 고르는 기준이 된다.
    @Builder.Default
    private long lastSaved = 0L;

    @Override
    public String getId() {
        return uuid;
    }

    public int getSkillLevel(String skillId) {
        if (skills == null) {
            return 0;
        }
        Integer value = skills.get(skillId);
        return value == null ? 0 : value;
    }

    public void addSkillLevel(String skillId, int amount) {
        if (skills == null) {
            skills = new HashMap<>();
        }
        skills.merge(skillId, amount, Integer::sum);
    }
}
