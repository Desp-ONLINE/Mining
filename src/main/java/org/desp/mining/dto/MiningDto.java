package org.desp.mining.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class MiningDto {
    private String user_id;
    private String uuid;
    private double fatigue;
}
