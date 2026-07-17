package org.desp.mining.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningSkillConfigDto {
    private String skill_id;
    private int maxLevel;
    /**
     * 스킬 레벨 -> 수치 (NO_FATIGUE/DOUBLE_DROP: 발동 확률 %, RARE_ORES: 확률 증가량 %)
     */
    private Map<Integer, Double> values;
    /**
     * 스킬 레벨 -> 해당 레벨로 올리는 데 필요한 스킬 포인트
     */
    private Map<Integer, Integer> pointCosts;
    /**
     * RARE_ORES 전용: 등장 확률이 증가할 아이템 ID 목록
     */
    private List<String> rareItems;

    public double getValue(int skillLevel) {
        if (skillLevel <= 0 || values == null || values.isEmpty()) {
            return 0;
        }
        Double exact = values.get(skillLevel);
        if (exact != null) {
            return exact;
        }
        // DB에 해당 레벨 값이 없으면 정의된 레벨 중 가장 가까운 아래 레벨의 값을 사용
        double result = 0;
        int bestLevel = 0;
        for (Map.Entry<Integer, Double> entry : values.entrySet()) {
            if (entry.getKey() <= skillLevel && entry.getKey() > bestLevel) {
                bestLevel = entry.getKey();
                result = entry.getValue();
            }
        }
        return result;
    }

    public int getPointCost(int skillLevel) {
        if (pointCosts != null) {
            Integer value = pointCosts.get(skillLevel);
            if (value != null) {
                return value;
            }
        }
        return 1;
    }
}
