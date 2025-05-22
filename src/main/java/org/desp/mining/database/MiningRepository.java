package org.desp.mining.database;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import lombok.Getter;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.desp.mining.dto.MiningDto;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.desp.mining.listener.MiningListener;

public class MiningRepository {

    private static MiningRepository instance;
    private final MongoCollection<Document> mining;
    @Getter
    private final Map<String, MiningDto> miningCache = new HashMap<>();

    private MiningRepository() {
        DatabaseRegister database = new DatabaseRegister();
        this.mining = database.getDatabase().getCollection("Mining");
    }

    public static synchronized MiningRepository getInstance() {
        if (instance == null) {
            instance = new MiningRepository();
        }
        return instance;
    }

    public MiningDto getPlayerMiningData(String uuid, String user_id) {
        Document query = mining.find(Filters.eq("uuid", uuid)).first();

        if (query != null) {
            // db에 값 있으면 다시 돌려주고.
            String userId = query.getString("user_id");
            double fatigue = query.getDouble("fatigue");
            return MiningDto.builder().user_id(userId).uuid(uuid).fatigue(fatigue).build();
        } else {
            // db에 값 없으면 초기값으로 return해줌
            return MiningDto.builder().user_id(user_id).uuid(uuid).fatigue(0).build();
        }
    }

    public boolean exitsPlayer(String user_id) {
        Document query = mining.find(Filters.eq("user_id", user_id)).first();

        return query != null;
    }

    public MiningDto getPlayer(String user_id) {
        Document query = mining.find(Filters.eq("user_id", user_id)).first();
        if (query != null) {
            String uuid = query.getString("uuid");
            return MiningListener.miningCache.get(uuid);
        } else {
            System.out.println("데이터 없음");
            return null;
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
            double newFatigue = Math.max(dto.getFatigue() - 1, 0);

            dto.setFatigue(newFatigue);
            MiningListener.miningCache.replace(uuid, dto);

            if(newFatigue < 1 && dto.getFatigue() >= 1){
                String userId = dto.getUser_id();
                Player player = Bukkit.getPlayer(userId);
                if(player == null){
                    return;
                }

                player.sendMessage("");
                player.sendMessage("§7[채광 알림] §f채광하느라 지친 §c피로§f가 싹~ 가라 앉은 것 같습니다.");
                player.sendMessage("");
                player.playSound(player, "minecraft:block.stem.break", SoundCategory.AMBIENT, 10, 2);
            }
        }
    }
}
