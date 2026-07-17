package org.desp.mining.skill;

import lombok.Getter;

@Getter
public enum MiningSkillType {

    NO_FATIGUE("피로도 무효", "일정 확률로 채광 시 피로도가 증가하지 않습니다."),
    DOUBLE_DROP("더블 드랍", "일정 확률로 채광 드랍 아이템을 2배로 획득합니다."),
    RARE_ORES("희귀 광물", "아래 광물 드랍 확률이 증가합니다.");

    private final String displayName;
    private final String description;

    MiningSkillType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static MiningSkillType fromInput(String input) {
        if (input == null) {
            return null;
        }
        for (MiningSkillType type : values()) {
            if (type.name().equalsIgnoreCase(input) || type.displayName.replace(" ", "").equals(input.replace(" ", ""))) {
                return type;
            }
        }
        return switch (input) {
            case "피로무효", "피로도무효" -> NO_FATIGUE;
            case "더블드랍", "더블드롭", "2배드랍" -> DOUBLE_DROP;
            case "희귀광물", "희귀", "희귀광석" -> RARE_ORES;
            default -> null;
        };
    }
}
