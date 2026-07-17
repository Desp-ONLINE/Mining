package org.desp.mining.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bson.Document;
import org.desp.mining.dto.MiningLevelConfigDto;
import org.desp.mining.dto.MiningSkillConfigDto;
import org.desp.mining.skill.MiningSkillType;

public class MiningConfigRepository {

    private static MiningConfigRepository instance;
    private final MongoCollection<Document> levelConfigCollection;
    private final MongoCollection<Document> skillConfigCollection;

    @Getter
    private MiningLevelConfigDto levelConfig;
    private final Map<String, MiningSkillConfigDto> skillConfigs = new HashMap<>();

    private MiningConfigRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.levelConfigCollection = database.getDatabase().getCollection("MiningLevelConfig");
        this.skillConfigCollection = database.getDatabase().getCollection("MiningSkillConfig");
        reload();
    }

    public static synchronized MiningConfigRepository getInstance() {
        if (instance == null) {
            instance = new MiningConfigRepository();
        }
        return instance;
    }

    public MiningSkillConfigDto getSkillConfig(MiningSkillType type) {
        return skillConfigs.get(type.name());
    }

    /**
     * DB에서 설정을 다시 읽는다. 문서가 없으면 기본값을 DB에 저장(시드)한다.
     */
    public void reload() {
        loadLevelConfig();
        for (MiningSkillType type : MiningSkillType.values()) {
            loadSkillConfig(type);
        }
    }

    // ======================= 레벨 설정 =======================

    private void loadLevelConfig() {
        Document query = levelConfigCollection.find(Filters.eq("config_id", "level")).first();
        if (query == null) {
            levelConfigCollection.insertOne(defaultLevelConfigDocument());
            query = levelConfigCollection.find(Filters.eq("config_id", "level")).first();
        }

        double expPerMining = query.get("exp_per_mining") == null ? 1.0
                : ((Number) query.get("exp_per_mining")).doubleValue();
        int maxLevel = query.get("max_level") == null ? 100
                : ((Number) query.get("max_level")).intValue();
        int skillPointsPerLevel = query.get("skill_points_per_level") == null ? 1
                : ((Number) query.get("skill_points_per_level")).intValue();

        Map<Integer, Double> requiredExp = new HashMap<>();
        Document requiredExpDoc = (Document) query.get("required_exp");
        if (requiredExpDoc != null) {
            for (String key : requiredExpDoc.keySet()) {
                requiredExp.put(Integer.parseInt(key), ((Number) requiredExpDoc.get(key)).doubleValue());
            }
        }

        levelConfig = MiningLevelConfigDto.builder()
                .expPerMining(expPerMining)
                .maxLevel(maxLevel)
                .skillPointsPerLevel(skillPointsPerLevel)
                .requiredExp(requiredExp)
                .build();
    }

    private Document defaultLevelConfigDocument() {
        Document requiredExp = new Document();
        for (int level = 1; level < 100; level++) {
            requiredExp.append(String.valueOf(level), 100.0 + (level - 1) * 25.0);
        }
        return new Document()
                .append("config_id", "level")
                .append("exp_per_mining", 1.0)
                .append("max_level", 100)
                .append("skill_points_per_level", 1)
                .append("required_exp", requiredExp);
    }

    // ======================= 스킬 설정 =======================

    private void loadSkillConfig(MiningSkillType type) {
        Document query = skillConfigCollection.find(Filters.eq("skill_id", type.name())).first();
        if (query == null) {
            skillConfigCollection.insertOne(defaultSkillConfigDocument(type));
            query = skillConfigCollection.find(Filters.eq("skill_id", type.name())).first();
        }

        int maxLevel = query.get("max_level") == null ? 10
                : ((Number) query.get("max_level")).intValue();

        Map<Integer, Double> values = new HashMap<>();
        Document valuesDoc = (Document) query.get("values");
        if (valuesDoc != null) {
            for (String key : valuesDoc.keySet()) {
                values.put(Integer.parseInt(key), ((Number) valuesDoc.get(key)).doubleValue());
            }
        }

        Map<Integer, Integer> pointCosts = new HashMap<>();
        Document pointCostsDoc = (Document) query.get("point_costs");
        if (pointCostsDoc != null) {
            for (String key : pointCostsDoc.keySet()) {
                pointCosts.put(Integer.parseInt(key), ((Number) pointCostsDoc.get(key)).intValue());
            }
        }

        List<String> rareItems = new ArrayList<>();
        List<?> rareItemsRaw = query.getList("rare_items", String.class, new ArrayList<>());
        for (Object item : rareItemsRaw) {
            rareItems.add(String.valueOf(item));
        }

        skillConfigs.put(type.name(), MiningSkillConfigDto.builder()
                .skill_id(type.name())
                .maxLevel(maxLevel)
                .values(values)
                .pointCosts(pointCosts)
                .rareItems(rareItems)
                .build());
    }

    private Document defaultSkillConfigDocument(MiningSkillType type) {
        int maxLevel = 10;
        double valuePerLevel;
        int pointCostPerLevel;
        switch (type) {
            case NO_FATIGUE -> {
                valuePerLevel = 3.0;
                pointCostPerLevel = 1;
            }
            case DOUBLE_DROP -> {
                valuePerLevel = 3.0;
                pointCostPerLevel = 2;
            }
            case RARE_ORES -> {
                valuePerLevel = 5.0;
                pointCostPerLevel = 3;
            }
            default -> {
                valuePerLevel = 1.0;
                pointCostPerLevel = 1;
            }
        }

        Document values = new Document();
        Document pointCosts = new Document();
        for (int level = 1; level <= maxLevel; level++) {
            values.append(String.valueOf(level), valuePerLevel * level);
            pointCosts.append(String.valueOf(level), pointCostPerLevel);
        }

        Document document = new Document()
                .append("skill_id", type.name())
                .append("max_level", maxLevel)
                .append("values", values)
                .append("point_costs", pointCosts);

        if (type == MiningSkillType.RARE_ORES) {
            document.append("rare_items", Arrays.asList("채광_다이아몬드", "채광_에메랄드"));
        }
        return document;
    }
}
