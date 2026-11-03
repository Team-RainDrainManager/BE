package com.rainbutler.domain.report.service;

import com.rainbutler.domain.report.dto.ReportDetailResponse;
import com.rainbutler.domain.report.dto.ReportListResponse;
import com.rainbutler.domain.report.dto.ReportSummaryResponse;
import com.rainbutler.domain.report.entity.Report;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.user.entity.Role;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.storage.StorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제보 목록 · 상세를 조회합니다.
 *
 * <p>내 제보와 관리자 목록은 최신순입니다. 상세는 제보자 본인과 관리자(ADMIN · CITY_OFFICIAL)만 볼 수
 * 있습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    /**
     * 내 제보 내역을 최신순으로 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 내 제보 목록
     */
    public ReportListResponse findMine(Long userId) {
        return toList(reportRepository.findByUser_IdOrderByCreatedAtDesc(userId));
    }

    /**
     * 관리자용 제보 목록을 최신순으로 조회합니다.
     *
     * @param status 처리 상태 (null이면 전체)
     * @return 제보 목록
     */
    public ReportListResponse findAll(ReportStatus status) {
        List<Report> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(status);
        return toList(reports);
    }

    /**
     * 제보 한 건을 상세 조회합니다.
     *
     * @param requesterId 로그인 사용자 id
     * @param reportId 제보 id
     * @return 제보 상세
     * @throws BusinessException 사용자 · 제보 없음, 볼 권한 없음 (FORBIDDEN)
     */
    public ReportDetailResponse findDetail(Long requesterId, Long reportId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        if (!isPrivileged(requester) && !report.getUser().getId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ReportDetailResponse.from(report, storageService.toReadUrl(report.getPhotoUrl()));
    }

    private ReportListResponse toList(List<Report> reports) {
        return new ReportListResponse(reports.stream()
                .map(ReportSummaryResponse::from)
                .toList());
    }

    private boolean isPrivileged(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.CITY_OFFICIAL;
    }
}
