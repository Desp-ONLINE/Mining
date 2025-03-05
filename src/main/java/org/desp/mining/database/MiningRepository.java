package org.desp.mining.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import lombok.Getter;
import org.bson.Document;
import org.desp.mining.dto.MiningDto;
import java.util.HashMap;
import java.util.Map;
import org.desp.mining.listener.MiningListener;

public class MiningRepository {

    private static MiningRepository instance;
    private final MongoCollection<Document> mining;
    @Getter
    private final Map<String, MiningDto> miningCache = new HashMap<>();

    private MiningRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.mining = database.getDatabase().getCollection("Mining");
//        loadMiningData();
    }

    public static synchronized MiningRepository getInstance() {
        if (instance == null) {
            instance = new MiningRepository();
        }
        return instance;
    }

    private void loadMiningData() {
        FindIterable<Document> documents = mining.find();
        for (Document document : documents) {
            String userId = document.getString("user_id");
            String uuid = document.getString("uuid");
            double fatigue = document.getDouble("fatigue");

            MiningDto miningDto = MiningDto.builder()
                    .user_id(userId)
                    .uuid(uuid)
                    .fatigue(fatigue)
                    .build();
            miningCache.put(uuid, miningDto);
        }
    }

    public MiningDto getPlayerMiningData(String uuid, String user_id) {
        Document query = mining.find(Filters.eq("uuid", uuid)).first();

        if (query != null) {
            // db에 값 있으면 다시 돌려주고.
//            loadMiningData();
            String userId = query.getString("user_id");
            double fatigue = query.getDouble("fatigue");
            return MiningDto.builder().user_id(userId).uuid(uuid).fatigue(fatigue).build();
        } else {
            // db에 값 없으면 초기값으로 return해줌
            return MiningDto.builder().user_id(user_id).uuid(uuid).fatigue(0).build();
        }
    }

    public void saveMining(String uuid, Map<String, MiningDto> miningCache) {
        Document query = mining.find(Filters.eq("uuid", uuid)).first();
        if (query != null) {
            MiningDto miningDto = miningCache.get(uuid);
            String userId = miningDto.getUser_id();
            double fatigue = miningDto.getFatigue();

            Document updateDocument = new Document()
                    .append("user_id", userId)
                    .append("uuid", uuid)
                    .append("fatigue", fatigue);

            mining.replaceOne(
                    Filters.eq("uuid", uuid),
                    updateDocument,
                    new ReplaceOptions().upsert(true)
            );
        } else {
            MiningDto miningDto = miningCache.get(uuid);
            Document document = new Document()
                    .append("user_id", miningDto.getUser_id())
                    .append("uuid", uuid)
                    .append("fatigue", miningDto.getFatigue());
            mining.insertOne(document);
        }
    }

    public void reduceFatigue() {
        for (String uuid : MiningListener.miningCache.keySet()) {
            MiningDto dto = MiningListener.miningCache.get(uuid);
            if (dto.getUser_id().equals("dople_L")) {
//                System.out.println("newFatigue = " + newFatigue);
                System.out.println("dto.getFatigue() = " + dto.getFatigue());
            }
            double newFatigue = Math.max(dto.getFatigue() - 1, 0);

            dto.setFatigue(newFatigue);
            MiningListener.miningCache.replace(uuid, dto);
        }
    }
}
