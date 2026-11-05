package org.desp.mining.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MiningBagItems {

    public static final String IRON = "채광_철";
    public static final String GOLD = "채광_금";
    public static final String LAPIS = "채광_청금석";
    public static final String DIAMOND = "채광_다이아몬드";
    public static final String EMERALD = "채광_에메랄드";

    // 래디언트스톤은 "채광_래디언트스톤_<등급>" 형식의 ID 를 쓴다.
    public static final String RADIANT_PREFIX = "채광_래디언트스톤_";
    private static final List<String> RADIANT_GRADES = List.of("일반", "레어", "에픽", "유니크", "레전더리");

    public static final String NETHERITE = "채광_네더라이트";

    public static final String FUSION_STEEL = "채광_퓨전스틸";
    // 퓨전스틸의 응집 아이템은 "_응집" 규칙을 따르지 않는다.
    public static final String FUSION_STEEL_CONDENSED = "채광_응집된퓨전스틸";

    // 응집 아이템은 하위 아이템 50개를 압축한 아이템이며, 기본 ID 는 하위 아이템 ID 뒤에 "_응집" 을 붙인 형태다.
    public static final String CONDENSED_SUFFIX = "_응집";
    public static final int CONDENSE_SIZE = 50;
    // 한 번에 회수할 수 있는 최대 수량 (하위 아이템 기준)
    public static final int MAX_WITHDRAW = 2000;

    // 채광하면 자동으로 가방에 쌓이고, 일괄 판매 대상이 되는 광물
    private static final List<String> ORE_IDS = List.of(IRON, GOLD, LAPIS, DIAMOND, EMERALD);
    private static final Set<String> ORE_SET = new HashSet<>(ORE_IDS);

    // 직접 넣기와 회수만 가능한 아이템 (자동 적립과 일괄 판매 대상이 아니다)
    private static final List<String> SPECIAL_IDS;
    // 가방에 보관할 수 있는 전체 아이템
    private static final List<String> STORABLE_IDS;
    private static final Set<String> STORABLE_SET;

    // 응집 ID 가 기본 규칙과 다른 아이템 (하위 ID -> 응집 ID, 응집 ID -> 하위 ID)
    private static final Map<String, String> CONDENSED_OVERRIDE = new HashMap<>();
    private static final Map<String, String> CONDENSED_OVERRIDE_REVERSE = new HashMap<>();

    private static final Map<String, String> ALIAS_TO_ID = new LinkedHashMap<>();

    static {
        ALIAS_TO_ID.put("철", IRON);
        ALIAS_TO_ID.put("금", GOLD);
        ALIAS_TO_ID.put("청금석", LAPIS);
        ALIAS_TO_ID.put("다이아몬드", DIAMOND);
        ALIAS_TO_ID.put("에메랄드", EMERALD);

        List<String> special = new ArrayList<>();
        for (String grade : RADIANT_GRADES) {
            String id = RADIANT_PREFIX + grade;
            special.add(id);
            ALIAS_TO_ID.put(grade, id);
        }
        special.add(NETHERITE);
        ALIAS_TO_ID.put("네더라이트", NETHERITE);
        special.add(FUSION_STEEL);
        ALIAS_TO_ID.put("퓨전스틸", FUSION_STEEL);
        SPECIAL_IDS = Collections.unmodifiableList(special);

        CONDENSED_OVERRIDE.put(FUSION_STEEL, FUSION_STEEL_CONDENSED);
        for (Map.Entry<String, String> entry : CONDENSED_OVERRIDE.entrySet()) {
            CONDENSED_OVERRIDE_REVERSE.put(entry.getValue(), entry.getKey());
        }

        List<String> storable = new ArrayList<>(ORE_IDS);
        storable.addAll(SPECIAL_IDS);
        STORABLE_IDS = Collections.unmodifiableList(storable);
        STORABLE_SET = new HashSet<>(STORABLE_IDS);
    }

    /** 채광 시 자동으로 가방에 쌓이는 광물인지 확인한다. */
    public static boolean isBagItem(String itemId) {
        return itemId != null && ORE_SET.contains(itemId);
    }

    public static String condensedIdOf(String itemId) {
        String override = CONDENSED_OVERRIDE.get(itemId);
        return override != null ? override : itemId + CONDENSED_SUFFIX;
    }

    /**
     * 인벤토리 아이템 ID 를 가방에 저장되는 하위 아이템 ID 로 바꾼다.
     * 가방에 넣을 수 없는 아이템이면 null 을 반환한다.
     */
    public static String baseIdOf(String itemId) {
        if (itemId == null) {
            return null;
        }
        if (STORABLE_SET.contains(itemId)) {
            return itemId;
        }
        String overrideBase = CONDENSED_OVERRIDE_REVERSE.get(itemId);
        if (overrideBase != null) {
            return overrideBase;
        }
        if (itemId.endsWith(CONDENSED_SUFFIX)) {
            String base = itemId.substring(0, itemId.length() - CONDENSED_SUFFIX.length());
            // 응집 ID 가 따로 정해진 아이템은 "_응집" 형식을 받지 않는다.
            if (CONDENSED_OVERRIDE.containsKey(base)) {
                return null;
            }
            return STORABLE_SET.contains(base) ? base : null;
        }
        return null;
    }

    /** 아이템 1개가 하위 아이템 몇 개에 해당하는지 반환한다. 가방 대상 아이템에만 쓴다. */
    public static int unitOf(String itemId) {
        return STORABLE_SET.contains(itemId) ? 1 : CONDENSE_SIZE;
    }

    public static String resolveAlias(String alias) {
        return ALIAS_TO_ID.get(alias);
    }

    /** 명령어에 입력할 수 있는 이름 목록 (탭 자동완성용) */
    public static List<String> getAliases() {
        return new ArrayList<>(ALIAS_TO_ID.keySet());
    }

    /** 채광 광물 목록 (일괄 판매 대상) */
    public static List<String> getOreIds() {
        return ORE_IDS;
    }

    /** 직접 넣기와 회수만 가능한 아이템 목록 (래디언트스톤, 네더라이트, 퓨전스틸) */
    public static List<String> getSpecialIds() {
        return SPECIAL_IDS;
    }

    /** 가방에 보관할 수 있는 전체 아이템 목록 */
    public static List<String> getStorableIds() {
        return STORABLE_IDS;
    }
}
