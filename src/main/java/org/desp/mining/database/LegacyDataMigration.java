package org.desp.mining.database;

import com.binggre.mongolibraryplugin.MongoLibraryPlugin;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.desp.mining.Mining;

/**
 * MongoLibraryPlugin 1.0.8+ 는 저장소 생성자에서 "id" 필드에 유니크 인덱스를 만든다.
 * 예전 코드가 남긴 문서에는 id 필드가 없어(null 취급) 인덱스 생성이 실패하므로,
 * 저장소 초기화 전에 id=uuid 를 채우고, uuid 중복 문서(과거 저장 경합의 흔적)는
 * 하나만 남기고 백업 컬렉션으로 옮긴다. 멱등이라 매 부팅마다 실행해도 안전하다.
 */
public final class LegacyDataMigration {

    private LegacyDataMigration() {
    }

    public static void run() {
        // 인덱스를 만드는 주체(MLP)와 같은 연결을 써야 같은 데이터를 보는 것이 보장된다
        MongoDatabase database = MongoLibraryPlugin.getInst().getMongoClient().getDatabase("Mining");
        migrate(database, "Mining", LegacyDataMigration::scorePlayer);
        migrate(database, "MiningBag", LegacyDataMigration::scoreBag);
    }

    private static void migrate(MongoDatabase database, String collectionName, Scorer scorer) {
        MongoCollection<Document> collection = database.getCollection(collectionName);

        // 1) id 가 없거나 null 인 문서에 id = uuid 를 채운다 (eq null 은 "필드 없음"도 매치한다)
        int filled = 0;
        for (Document doc : collection.find(Filters.eq("id", null))) {
            String uuid = doc.getString("uuid");
            if (uuid == null) {
                continue; // uuid 조차 없는 문서는 판단할 수 없으므로 건드리지 않는다
            }
            collection.updateOne(Filters.eq("_id", doc.get("_id")),
                    new Document("$set", new Document("id", uuid)));
            filled++;
        }
        if (filled > 0) {
            Mining.getInstance().getLogger().info(
                    "[채광] 마이그레이션: " + collectionName + " 문서 " + filled + "건에 id 필드를 채웠습니다.");
        }

        // 2) 같은 id 문서가 여러 개면 유니크 인덱스 생성이 실패하므로 하나만 남긴다.
        //    데이터가 더 많은 쪽(레벨/보유량 기준)을 남기고 나머지는 백업 컬렉션으로 옮긴다.
        Map<String, List<Document>> byId = new HashMap<>();
        for (Document doc : collection.find(Filters.ne("id", null))) {
            byId.computeIfAbsent(doc.getString("id"), k -> new ArrayList<>()).add(doc);
        }
        MongoCollection<Document> backup = database.getCollection(collectionName + "_backup");
        for (Map.Entry<String, List<Document>> entry : byId.entrySet()) {
            List<Document> docs = entry.getValue();
            if (docs.size() <= 1) {
                continue;
            }
            Document keeper = docs.get(0);
            for (Document candidate : docs) {
                if (scorer.score(candidate) > scorer.score(keeper)) {
                    keeper = candidate;
                }
            }
            for (Document doc : docs) {
                if (doc == keeper) {
                    continue;
                }
                backup.insertOne(doc);
                collection.deleteOne(Filters.eq("_id", doc.get("_id")));
                Mining.getInstance().getLogger().warning(
                        "[채광] 마이그레이션: " + collectionName + " 의 uuid 중복 문서를 "
                                + collectionName + "_backup 으로 이동했습니다. (uuid=" + entry.getKey() + ")");
            }
        }
    }

    private static double scorePlayer(Document doc) {
        double level = doc.get("level") == null ? 0 : ((Number) doc.get("level")).doubleValue();
        double exp = doc.get("exp") == null ? 0 : ((Number) doc.get("exp")).doubleValue();
        return level * 1_000_000 + exp;
    }

    private static double scoreBag(Document doc) {
        Document bag = (Document) doc.get("bag");
        if (bag == null) {
            return 0;
        }
        double total = 0;
        for (String itemId : bag.keySet()) {
            Object value = bag.get(itemId);
            if (value instanceof Number number) {
                total += number.doubleValue();
            }
        }
        return total;
    }

    private interface Scorer {
        double score(Document doc);
    }
}
