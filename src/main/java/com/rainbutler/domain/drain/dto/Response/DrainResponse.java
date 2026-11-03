package com.rainbutler.domain.drain.dto.Response;

import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 지도 · 목록에 보여주는 빗물받이 한 건입니다.
 *
 * <p>입양 전에는 {@code level=0, xp=0}입니다. 이름은 입양 별명 → 등록 이름 → {@code 빗물받이 #id} 순으로
 * 정해집니다.
 */
@Getter
@Builder
public class DrainResponse {
    private Long id;
    private String name;
    private String address;
    private Double longitude;
    private Double latitude;
    private DrainAdoptionStatus adoptionStatus;
    /** 화면 필터용 표시 상태 (미입양 · 정상 · 점검 지연 · 막힘 제보) */
    private DrainDisplayStatus displayStatus;
    private String displayStatusLabel;
    private boolean isRisk;
    private Integer dirtLevel;
    private Integer level;
    private String levelTitle;
    private Integer xp;
}
