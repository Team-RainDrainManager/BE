package com.rainbutler.domain.drain.dto.Response;

import jdk.jshell.Snippet;

import java.time.LocalDateTime;

public record DrainDetailResponse (
        Long id,
        String status,
        String adoptionStatus,
        Double latitude,
        Double longitude,
        String photoUrl,
        Integer dirtLevel,
        AdoptionResponse adoption
) {
    public record AdoptionResponse(
            Long adoptionId,
            String nickname,
            Integer level,
            Integer score,
            LocalDateTime adoptedAt,
            LocalDateTime lastManagedAt,
            LocalDateTime nextManagementAt
    ) {
    }
}
