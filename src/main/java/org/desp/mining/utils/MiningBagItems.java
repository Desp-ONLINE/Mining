package org.desp.mining.utils;

import java.util.Arrays;
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

    private static final List<String> ITEM_IDS = Arrays.asList(IRON, GOLD, LAPIS, DIAMOND, EMERALD);
    private static final Set<String> ID_SET = new HashSet<>(ITEM_IDS);
    private static final Map<String, String> ALIAS_TO_ID = new LinkedHashMap<>();

    static {
        ALIAS_TO_ID.put("철", IRON);
        ALIAS_TO_ID.put("금", GOLD);
        ALIAS_TO_ID.put("청금석", LAPIS);
        ALIAS_TO_ID.put("다이아몬드", DIAMOND);
        ALIAS_TO_ID.put("에메랄드", EMERALD);
    }

    public static boolean isBagItem(String itemId) {
        return itemId != null && ID_SET.contains(itemId);
    }

    public static String resolveAlias(String alias) {
        return ALIAS_TO_ID.get(alias);
    }

    public static List<String> getItemIds() {
        return ITEM_IDS;
    }
}
