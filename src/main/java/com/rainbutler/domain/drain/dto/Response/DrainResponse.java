package com.rainbutler.domain.drain.dto.Response;

import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.entity.DrainDisplayStatus;
import com.rainbutler.domain.request.dto.RainRequestSummary;
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
    /**
     * 지도 · 목록에 표시하는 상태 키입니다. (unadopted · normal · overdue · blocked 중 하나)
     *
     * <p>프론트 지도 화면이 그대로 쓸 수 있도록 {@link DrainDisplayStatus#getKey()} 값을 내려줍니다.
     */
    private String status;
    /** 화면 필터용 표시 상태 (미입양 · 정상 · 점검 지연 · 막힘 제보) */
    private DrainDisplayStatus displayStatus;
    private String displayStatusLabel;
    private boolean isRisk;
    private Integer dirtLevel;
    private Integer level;
    private String levelTitle;
    private Integer xp;
    /**
     * 이번 비(오늘 · 내일 진행 중 예보) 대신 점검 요청. 요청이 없거나 놓침 · 종료 · 취소면 null.
     * 공개 API라 입양자 · 수락자 정보는 없습니다.
     */
    private RainRequestSummary rainRequest;
}
