package com.rainbutler.domain.drain.dto.Response;

import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DrainResponse {
    private Long id;
    private Double longitude;
    private Double latitude;
    private DrainAdoptionStatus adoptionStatus;
    private boolean isRisk;
}
