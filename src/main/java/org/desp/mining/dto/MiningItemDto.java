package org.desp.mining.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningItemDto {
    private String item_id;
    private double itemDropPercentage;
    // 한 번 드랍될 때 지급되는 기본 개수 (DB 에 amount 가 없으면 1)
    @Builder.Default
    private int amount = 1;
}
