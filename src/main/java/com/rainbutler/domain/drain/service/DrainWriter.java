package com.rainbutler.domain.drain.service;

import com.rainbutler.domain.drain.dto.Request.DrainCreateRequest;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 빗물받이 생성의 DB 쓰기를 맡습니다. (짧은 트랜잭션)
 *
 * <p>AI 판정(외부 호출)은 트랜잭션 밖 {@code DrainService}에서 끝내고, 여기서는 저장만 합니다.
 * 느린 외부 호출이 DB 커넥션을 잡지 않게 분리했습니다.
 */
@Component
@RequiredArgsConstructor
public class DrainWriter {

    private final DrainRepository drainRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;

    /**
     * 저장 결과입니다.
     *
     * @param drain 저장된 빗물받이
     * @param riskReportId 함께 생성된 제보 id (없으면 null)
     */
    public record SavedDrain(Drain drain, Long riskReportId) {
    }

    /**
     * 빗물받이와 제보(있으면)를 저장합니다.
     *
     * @param userId 등록자 id
     * @param request 생성 요청
     * @param photoKey 사진 key
     * @param dirtLevel AI 판정 더러움 단계
     * @param isRisk 저장할 위험 여부
     * @param reportDescription 제보 사유 (null이면 제보 없음)
     * @return 저장 결과
     */
    @Transactional
    public SavedDrain save(
            Long userId,
            DrainCreateRequest request,
            String photoKey,
            int dirtLevel,
            boolean isRisk,
            String reportDescription) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        Drain saved = drainRepository.save(Drain.builder()
                .registeredBy(user)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .photoUrl(photoKey)
                .dirtLevel(dirtLevel)
                .status(DrainStatus.ACTIVE)
                .isRisk(isRisk)
                .build());

        Long riskReportId = null;
        if (reportDescription != null) {
            Report report = Report.builder()
                    .user(user)
                    .drain(saved)
                    .type(ReportType.BLOCKAGE)
                    .description(reportDescription)
                    .photoUrl(photoKey)
                    .build();
            riskReportId = reportRepository.save(report).getId();
        }
        return new SavedDrain(saved, riskReportId);
    }
}
