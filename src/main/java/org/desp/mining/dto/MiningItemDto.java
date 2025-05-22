package org.desp.mining.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningItemDto {
    private String item_id;
    private double itemDropPercentage;
}
