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
            double itemDropPercentage = document.getDouble("itemDropPercentage");

            MiningItemDto miningItemDto = MiningItemDto.builder()
                    .item_id(item_id)
                    .itemDropPercentage(itemDropPercentage)
                    .build();
            miningCache.put(item_id, miningItemDto);
        }
    }

    public Map<String, MiningItemDto> getMiningCache() {
        loadMiningData();
        return miningCache;
    }

}
