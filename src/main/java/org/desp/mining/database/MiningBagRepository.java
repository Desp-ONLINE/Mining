package org.desp.mining.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import lombok.Getter;
import org.bson.Document;
import org.desp.mining.dto.MiningBagDto;

import java.util.HashMap;
import java.util.Map;

public class MiningBagRepository {

    private static MiningBagRepository instance;
    private final MongoCollection<Document> miningBag;
    @Getter
    private final Map<String, MiningBagDto> bagCache = new HashMap<>();

    private MiningBagRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.miningBag = database.getDatabase().getCollection("MiningBag");
    }

    public static synchronized MiningBagRepository getInstance() {
        if (instance == null) {
            instance = new MiningBagRepository();
        }
        return instance;
    }

    public MiningBagDto getPlayerBag(String uuid, String user_id) {
        Document query = miningBag.find(Filters.eq("uuid", uuid)).first();
        if (query != null) {
            String userId = query.getString("user_id");
            Map<String, Integer> bag = new HashMap<>();
            Document bagDoc = (Document) query.get("bag");
            if (bagDoc != null) {
                for (String key : bagDoc.keySet()) {
                    bag.put(key, ((Number) bagDoc.get(key)).intValue());
                }
            }
            return MiningBagDto.builder().user_id(userId).uuid(uuid).bag(bag).build();
        }
        return MiningBagDto.builder().user_id(user_id).uuid(uuid).bag(new HashMap<>()).build();
    }

    public void saveBag(String uuid, MiningBagDto dto) {
        if (dto == null) {
            return;
        }
        Document bagDoc = new Document();
        if (dto.getBag() != null) {
            for (Map.Entry<String, Integer> entry : dto.getBag().entrySet()) {
                bagDoc.append(entry.getKey(), entry.getValue());
            }
        }
        Document document = new Document()
                .append("user_id", dto.getUser_id())
                .append("uuid", uuid)
                .append("bag", bagDoc);

        miningBag.replaceOne(
                Filters.eq("uuid", uuid),
                document,
                new ReplaceOptions().upsert(true)
        );
    }
}
