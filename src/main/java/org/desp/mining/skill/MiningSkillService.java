package org.desp.mining.skill;

import com.binggre.binggreapi.utils.ColorManager;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.desp.mining.database.MiningConfigRepository;
import org.desp.mining.dto.MiningDto;
import org.desp.mining.dto.MiningLevelConfigDto;
import org.desp.mining.dto.MiningSkillConfigDto;

public class MiningSkillService {

    private static final Random random = new Random();

    private static MiningLevelConfigDto levelConfig() {
        return MiningConfigRepository.getInstance().getLevelConfig();
    }

    private static MiningSkillConfigDto skillConfig(MiningSkillType type) {
        return MiningConfigRepository.getInstance().getSkillConfig(type);
    }

    // ======================= 레벨 / 경험치 =======================

    public static int getMaxLevel() {
        return levelConfig().getMaxLevel();
    }

    public static double getExpPerMining() {
        return levelConfig().getExpPerMining();
    }

    public static int getSkillPointsPerLevel() {
        return levelConfig().getSkillPointsPerLevel();
    }

    /**
     * level -> level+1 로 올라가기 위해 필요한 경험치 (DB에서 레벨별 설정)
     */
    public static double getRequiredExp(int level) {
        return levelConfig().getRequiredExp(level);
    }

    /**
     * 채광 1회에 대한 경험치를 지급하고, 레벨업 처리(스킬 포인트 지급 포함)를 한다.
     */
    public static void addMiningExp(Player player, MiningDto miningData) {
        if (miningData == null) {
            return;
        }
        if (miningData.getLevel() <= 0) {
            miningData.setLevel(1);
        }
        if (miningData.getLevel() >= getMaxLevel()) {
            return;
        }

        miningData.setExp(miningData.getExp() + getExpPerMining());

        int levelUps = 0;
        while (miningData.getLevel() < getMaxLevel()
                && miningData.getExp() >= getRequiredExp(miningData.getLevel())) {
            miningData.setExp(miningData.getExp() - getRequiredExp(miningData.getLevel()));
            miningData.setLevel(miningData.getLevel() + 1);
            levelUps++;
        }

        if (levelUps > 0) {
            int gainedPoints = levelUps * getSkillPointsPerLevel();
            miningData.setSkillPoints(miningData.getSkillPoints() + gainedPoints);

            // 레벨업 디버깅용 로그
            System.out.println("[Mining][DEBUG] 채광 레벨업: " + player.getName()
                    + " -> Lv." + miningData.getLevel()
                    + " (+" + levelUps + "레벨, 스킬포인트 +" + gainedPoints
                    + ", 잔여 경험치 " + miningData.getExp() + ")");

            player.sendMessage("");
            player.sendMessage("§7[채광 알림] §f채광 레벨이 §e" + miningData.getLevel()
                    + " §f레벨이 되었습니다! §7§o(스킬 포인트 +" + gainedPoints + ", /채광스킬)");
            player.sendMessage("");
            player.playSound(player, "minecraft:entity.player.levelup", SoundCategory.AMBIENT, 10, 1);
        }
    }

    // ======================= 스킬 공통 =======================

    public static int getSkillMaxLevel(MiningSkillType type) {
        MiningSkillConfigDto config = skillConfig(type);
        return config == null ? 0 : config.getMaxLevel();
    }

    /**
     * 스킬 레벨에 따른 수치 (DB에서 스킬 레벨별 설정, double)
     */
    public static double getSkillValue(MiningSkillType type, int skillLevel) {
        MiningSkillConfigDto config = skillConfig(type);
        return config == null ? 0 : config.getValue(skillLevel);
    }

    /**
     * 해당 스킬을 targetLevel 로 올리는 데 필요한 스킬 포인트 (DB에서 스킬/레벨별 설정)
     */
    public static int getPointCost(MiningSkillType type, int targetLevel) {
        MiningSkillConfigDto config = skillConfig(type);
        return config == null ? 1 : config.getPointCost(targetLevel);
    }

    /**
     * 스킬 레벨을 최대 levels 만큼 올린다. 레벨마다 필요한 포인트가 다르며,
     * 포인트가 부족하거나 최대 레벨에 도달하면 중단한다. 올라간 레벨 수를 반환한다.
     */
    public static int invest(MiningDto miningData, MiningSkillType type, int levels) {
        if (miningData == null || levels <= 0) {
            return 0;
        }
        int maxLevel = getSkillMaxLevel(type);
        int levelUps = 0;
        while (levelUps < levels) {
            int nextLevel = miningData.getSkillLevel(type.name()) + 1;
            if (nextLevel > maxLevel) {
                break;
            }
            int cost = getPointCost(type, nextLevel);
            if (miningData.getSkillPoints() < cost) {
                break;
            }
            miningData.setSkillPoints(miningData.getSkillPoints() - cost);
            miningData.addSkillLevel(type.name(), 1);
            levelUps++;
        }
        return levelUps;
    }

    // ======================= NO_FATIGUE =======================

    public static boolean rollNoFatigue(MiningDto miningData) {
        int skillLevel = miningData == null ? 0 : miningData.getSkillLevel(MiningSkillType.NO_FATIGUE.name());
        if (skillLevel <= 0) {
            return false;
        }
        return random.nextDouble(100) < getSkillValue(MiningSkillType.NO_FATIGUE, skillLevel);
    }

    // ======================= DOUBLE_DROP =======================

    public static boolean rollDoubleDrop(MiningDto miningData) {
        int skillLevel = miningData == null ? 0 : miningData.getSkillLevel(MiningSkillType.DOUBLE_DROP.name());
        if (skillLevel <= 0) {
            return false;
        }
        return random.nextDouble(100) < getSkillValue(MiningSkillType.DOUBLE_DROP, skillLevel);
    }

    // ======================= RARE_ORES =======================

    /**
     * 스킬 레벨에 따른 희귀 광물 확률 증가량 n (% 단위, 기존 확률 대비 상대 증가)
     */
    public static double getRareOreBoostPercent(int skillLevel) {
        return getSkillValue(MiningSkillType.RARE_ORES, skillLevel);
    }

    public static List<String> getRareOreItemIds() {
        MiningSkillConfigDto config = skillConfig(MiningSkillType.RARE_ORES);
        if (config == null || config.getRareItems() == null) {
            return Collections.emptyList();
        }
        return config.getRareItems();
    }

    // ======================= 표시용 =======================

    /**
     * 해당 스킬 레벨의 효과를 표시용 문자열로 만든다. (파스텔톤)
     */
    public static String describeEffect(MiningSkillType type, int skillLevel) {
        return switch (type) {
            case NO_FATIGUE -> ColorManager.format("#FDFFB6 " + getSkillValue(type, skillLevel) + "% #C9C9C9 확률로 피로도 증가 무효");
            case DOUBLE_DROP -> ColorManager.format("#FDFFB6 " + getSkillValue(type, skillLevel) + "% #C9C9C9 확률로 드랍 아이템 x2");
            case RARE_ORES -> ColorManager.format("#FDFFB6 " + getRareOreBoostPercent(skillLevel) + "% #C9C9C9 드랍 확률 증가");
        };
    }
}
