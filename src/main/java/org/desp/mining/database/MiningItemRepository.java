package org.desp.mining.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.desp.mining.dto.MiningItemDto;

public class MiningItemRepository {

    private static MiningItemRepository instance;
    private final MongoCollection<Document> miningDropItem;
    private final Map<String, MiningItemDto> miningCache = new HashMap<>();

    private MiningItemRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.miningDropItem = database.getDatabase().getCollection("MiningDropItem");
        loadMiningData();
    }

    public static synchronized MiningItemRepository getInstance() {
        if (instance == null) {
            instance = new MiningItemRepository();
        }
        return instance;
    }

    private void loadMiningData() {
        FindIterable<Document> documents = miningDropItem.find();
        for (Document document : documents) {
            String item_id = document.getString("item_id");
            double itemDropPercentage = ((Number) document.get("itemDropPercentage")).doubleValue();
            // amount 표기가 없거나 1보다 작으면 1개로 지급한다.
            Object rawAmount = document.get("amount");
            int amount = rawAmount instanceof Number number ? Math.max(1, number.intValue()) : 1;

            MiningItemDto miningItemDto = MiningItemDto.builder()
                    .item_id(item_id)
                    .itemDropPercentage(itemDropPercentage)
                    .amount(amount)
                    .build();
            miningCache.put(item_id, miningItemDto);
        }
    }

    public Map<String, MiningItemDto> getMiningCache() {
        loadMiningData();
        return miningCache;
    }

    /** DB 를 다시 읽지 않고 현재 캐시에서 드랍 아이템 정보를 꺼낸다. */
    public MiningItemDto getCachedItem(String itemId) {
        return miningCache.get(itemId);
    }

}
