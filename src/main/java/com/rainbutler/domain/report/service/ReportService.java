package com.rainbutler.domain.report.service;

import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.drain.service.DrainAiClassifier;
import com.rainbutler.domain.point.entity.PointHistory;
import com.rainbutler.domain.point.entity.PointSourceType;
import com.rainbutler.domain.point.entity.PointType;
import com.rainbutler.domain.point.repository.PointHistoryRepository;
import com.rainbutler.domain.report.dto.ReportClearRequest;
import com.rainbutler.domain.report.dto.ReportCreateRequest;
import com.rainbutler.domain.report.dto.ReportCreateResponse;
import com.rainbutler.domain.report.dto.ReportDetailResponse;
import com.rainbutler.domain.report.dto.ReportReviewRequest;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.entity.ReportType;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제보를 접수하고 관리자 승인 · 반려를 처리합니다.
 *
 * <p>제보는 접수하면 {@code PENDING}이고, 관리자가 승인하면 {@code APPROVED}가 되면서 경험치를 지급합니다.
 * 반려는 사유를 남기고 경험치를 지급하지 않습니다.
 *
 * <p>기존 빗물받이의 막힘 제보는 AI가 청소 불가(막힘(3등급) 또는 {@code needsReport=true})로
 * 판정해야
 * 접수됩니다. 이 제보는 {@code blocksAdoption=true}로 저장되어, 반려되면 자동으로 풀리고 승인 후에는
 * 관리자가 {@link #clear}로 해제할 때까지 빗물받이 입양을 막습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final DrainRepository drainRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final DrainAiClassifier drainAiClassifier;
    private final StorageService storageService;
    private final PolicyProperties policyProperties;
    private final Clock clock;

    /** 입양을 막는 제보 상태 (반려 · 해제되면 풀린다) */
    private static final List<ReportStatus> BLOCKING_STATUSES =
            List.of(ReportStatus.PENDING, ReportStatus.APPROVED);

    /**
     * 제보를 접수합니다.
     *
     * <p>제보 종류에 따라 필요한 사진 · 설명 · 새 위치를 검사합니다. 촬영 위치를 보내지 않으면 빗물받이
     * 위치를 씁니다.
     *
     * @param userId 제보자 id
     * @param request 제보 생성 요청
     * @return 접수된 제보 id
     * @throws BusinessException 사용자 · 빗물받이 없음, 종류별 필수 값 누락 (INVALID_INPUT),
     *     사진 검증 실패
     */
    @Transactional
    public ReportCreateResponse create(Long userId, ReportCreateRequest request) {
        validate(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Drain drain = request.drainId() == null
                ? null
                : drainRepository.findById(request.drainId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.DRAIN_NOT_FOUND));

        String photoKey = isBlank(request.photoKey())
                ? null
                : storageService.verifyUploaded(userId, request.photoKey(), UploadPurpose.REPORT);
        Double latitude = request.latitude();
        Double longitude = request.longitude();
        if (latitude == null && drain != null) {
            latitude = drain.getLatitude();
            longitude = drain.getLongitude();
        }

        // 기존 빗물받이의 막힘 제보만 AI로 청소 불가 여부를 확인한다.
        boolean blocksAdoption = request.type() == ReportType.BLOCKAGE
                && requireUncleanable(drain, photoKey);

        Report report = Report.builder()
                .user(user)
                .drain(drain)
                .type(request.type())
                .description(trimToNull(request.description()))
                .photoUrl(photoKey)
                .latitude(latitude)
                .longitude(longitude)
                .newLatitude(request.newLatitude())
                .newLongitude(request.newLongitude())
                .blocksAdoption(blocksAdoption)
                .build();
        return new ReportCreateResponse(reportRepository.save(report).getId(), blocksAdoption);
    }

    /**
     * 막힘 제보 사진을 AI로 판독해 청소할 수 없는 상태인지 확인합니다.
     *
     * <p>이미 같은 빗물받이에 입양을 막는 제보가 있으면 중복 접수하지 않습니다. AI 기준은
     * 막힘(3등급) 또는 {@code needsReport=true}입니다.
     *
     * @param drain 대상 빗물받이
     * @param photoKey 제보 사진 key
     * @return 입양을 막아야 하면 true
     * @throws BusinessException 이미 차단 제보가 있으면 (REPORT_ALREADY_BLOCKED), 청소 불가 기준이
     *     아니면 (REPORT_NOT_UNCLEANABLE)
     */
    private boolean requireUncleanable(Drain drain, String photoKey) {
        if (reportRepository.existsByDrain_IdAndBlocksAdoptionTrueAndStatusIn(
                drain.getId(), BLOCKING_STATUSES)) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_BLOCKED);
        }

        DrainAiClassifier.Assessment assessment =
                drainAiClassifier.assess(storageService.toReadUrl(photoKey));
        if (!assessment.needsReport()
                && assessment.grade() != DrainAiClassifier.GRADE_BLOCKED) {
            throw new BusinessException(ErrorCode.REPORT_NOT_UNCLEANABLE);
        }
        return true;
    }

    /**
     * 관리자가 제보를 승인하거나 반려합니다.
     *
     * <p>승인하면 제보자에게 경험치를 지급합니다. 반려는 사유가 필요하고 경험치를 지급하지 않습니다.
     * 이미 처리된 제보는 다시 처리할 수 없습니다.
     *
     * @param adminId 처리하는 관리자 · 시청 관계자 id
     * @param reportId 제보 id
     * @param request 승인 여부 · 코멘트
     * @return 처리된 제보 상세
     * @throws BusinessException 사용자 · 제보 없음, 이미 처리됨 (REPORT_ALREADY_PROCESSED),
     *     반려 사유 누락 (INVALID_INPUT)
     */
    @Transactional
    public ReportDetailResponse review(
            Long adminId, Long reportId, ReportReviewRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.isPending()) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String comment = trimToNull(request.adminComment());
        LocalDateTime now = LocalDateTime.now(clock);

        if (Boolean.TRUE.equals(request.approved())) {
            report.approve(admin, comment, now);
            reward(report);
        } else {
            if (comment == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유를 입력해 주세요.");
            }
            report.reject(admin, comment, now);
        }

        return ReportDetailResponse.from(report, storageService.toReadUrl(report.getPhotoUrl()));
    }

    /**
     * 승인된 제보의 입양 차단을 해제합니다.
     *
     * <p>승인된 막힘 제보는 관리자가 해제할 때까지 빗물받이를 입양할 수 없게 합니다. 반려된 제보는
     * 검토 단계에서 이미 차단이 풀리므로 해제할 필요가 없습니다. 입양을 막지 않는 승인 제보도 해제할
     * 수 없습니다.
     *
     * @param adminId 처리하는 관리자 · 시청 관계자 id
     * @param reportId 제보 id
     * @param request 해제 코멘트 (없으면 null)
     * @return 처리된 제보 상세
     * @throws BusinessException 사용자 · 제보 없음, 승인된 막힘 제보가 아니면 (REPORT_NOT_CLEARABLE)
     */
    @Transactional
    public ReportDetailResponse clear(
            Long adminId, Long reportId, ReportClearRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.APPROVED || !report.isBlocksAdoption()) {
            throw new BusinessException(ErrorCode.REPORT_NOT_CLEARABLE);
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String comment = request == null ? null : trimToNull(request.adminComment());
        report.clear(admin, comment, LocalDateTime.now(clock));

        return ReportDetailResponse.from(report, storageService.toReadUrl(report.getPhotoUrl()));
    }

    /**
     * 승인된 제보의 경험치를 제보자에게 지급합니다.
     */
    private void reward(Report report) {
        int xp = policyProperties.reportXp();
        if (xp <= 0) {
            return;
        }
        pointHistoryRepository.save(PointHistory.builder()
                .user(report.getUser())
                .sourceType(PointSourceType.REPORT)
                .sourceId(report.getId())
                .amount(xp)
                .type(PointType.EARN)
                .description("제보 승인")
                .build());
        report.getUser().addPoint(xp);
    }

    /**
     * 제보 종류별 필수 값을 검사합니다.
     */
    private void validate(ReportCreateRequest request) {
        boolean hasPhoto = !isBlank(request.photoKey());
        boolean hasDescription = !isBlank(request.description());

        // NEW_DRAIN은 아직 빗물받이가 없고, 나머지는 대상 빗물받이가 필수다.
        if (request.type() == ReportType.NEW_DRAIN) {
            if (request.drainId() != null) {
                throw invalid("신규 등록 제보에는 빗물받이 id를 보내지 마세요.");
            }
            if (request.latitude() == null || request.longitude() == null) {
                throw invalid("신규 등록 제보는 촬영 위치가 필요해요.");
            }
        } else if (request.drainId() == null) {
            throw invalid("빗물받이 id를 보내 주세요.");
        }

        switch (request.type()) {
            case ODOR -> {
                if (!hasPhoto && !hasDescription) {
                    throw invalid("악취 제보는 사진이나 설명 중 하나가 필요해요.");
                }
            }
            case REMOVAL -> {
                requirePhoto(hasPhoto);
                if (!hasDescription) {
                    throw invalid("철거 제보는 사유를 입력해 주세요.");
                }
            }
            case LOCATION_FIX -> {
                requirePhoto(hasPhoto);
                if ((request.newLatitude() == null) != (request.newLongitude() == null)) {
                    throw invalid("새 위치의 위도와 경도를 함께 보내 주세요.");
                }
                if (request.newLatitude() == null) {
                    throw invalid("위치 수정 제보는 새 위치가 필요해요.");
                }
            }
            default -> requirePhoto(hasPhoto);
        }

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw invalid("촬영 위치의 위도와 경도를 함께 보내 주세요.");
        }
    }

    private void requirePhoto(boolean hasPhoto) {
        if (!hasPhoto) {
            throw invalid("제보 사진을 올려 주세요.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }
}
