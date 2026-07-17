package org.desp.mining.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningLevelConfigDto {
    private double expPerMining;
    private int maxLevel;
    private int skillPointsPerLevel;
    /**
     * 레벨 n -> n+1 로 올라가기 위해 필요한 경험치
     */
    private Map<Integer, Double> requiredExp;

    public double getRequiredExp(int level) {
        if (requiredExp != null) {
            Double value = requiredExp.get(level);
            if (value != null) {
                return value;
            }
        }
        // DB에 해당 레벨 값이 없으면 기본 공식으로 대체
        return 100.0 + (level - 1) * 25.0;
    }
}
