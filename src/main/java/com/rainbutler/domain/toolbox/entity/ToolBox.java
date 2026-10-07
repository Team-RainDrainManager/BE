package com.rainbutler.domain.toolbox.entity;

import com.rainbutler.global.entity.BaseEntity;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 청소 도구함입니다. (tool_box 테이블)
 */
@Entity
@Table(name = "tool_box")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ToolBox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** 구비된 세트 수 */
    @Column(nullable = false)
    private Integer totalCount;

    /** 대여 가능한 세트 수 */
    @Column(nullable = false)
    private Integer availableCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolBoxStatus status;

    /** 필수 값으로 생성합니다. 초기 상태·기본값은 여기서 채웁니다. */
    @Builder
    private ToolBox(String name, Double latitude, Double longitude, Integer totalCount) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.totalCount = totalCount;
        this.availableCount = totalCount;
        this.status = statusOf(totalCount);
    }

    /**
     * 도구 1세트를 대여합니다. 남은 수량이 0이 되면 ALL_RENTED로 바뀝니다.
     *
     * @throws BusinessException 남은 도구가 없을 때 (TOOL_BOX_EMPTY)
     */
    public void rent() {
        if (this.availableCount <= 0) {
            throw new BusinessException(ErrorCode.TOOL_BOX_EMPTY);
        }
        this.availableCount--;
        this.status = statusOf(this.availableCount);
    }

    /**
     * 도구 1세트를 반납합니다. 남은 수량이 생기면 AVAILABLE로 바뀝니다.
     *
     * @throws BusinessException 반납 후 수량이 총수량을 넘을 때 (TOOL_RETURN_MISMATCH)
     */
    public void giveBack() {
        if (this.availableCount >= this.totalCount) {
            throw new BusinessException(ErrorCode.TOOL_RETURN_MISMATCH);
        }
        this.availableCount++;
        this.status = statusOf(this.availableCount);
    }

    /**
     * 남은 수량으로 도구함 상태를 정합니다.
     */
    private static ToolBoxStatus statusOf(int availableCount) {
        return availableCount == 0 ? ToolBoxStatus.ALL_RENTED : ToolBoxStatus.AVAILABLE;
    }
}
