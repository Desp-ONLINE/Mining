package org.desp.mining.dto;

import com.binggre.mongolibraryplugin.base.MongoData;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningBagDto implements MongoData<String> {
    private String user_id;
    private String uuid;
    private Map<String, Integer> bag;
    // 마지막 저장 시각(ms). 로드 시 Redis/Mongo 중 더 최신 데이터를 고르는 기준이 된다.
    private long lastSaved;

    @Override
    public String getId() {
        return uuid;
    }

    public int getCount(String itemId) {
        if (bag == null) {
            return 0;
        }
        Integer value = bag.get(itemId);
        return value == null ? 0 : value;
    }

    public void addCount(String itemId, int amount) {
        if (bag == null) {
            bag = new HashMap<>();
        }
        bag.merge(itemId, amount, Integer::sum);
    }

    public void clearCount(String itemId) {
        if (bag != null) {
            bag.remove(itemId);
        }
    }

    public void clearAll() {
        if (bag != null) {
            bag.clear();
        }
    }
}
