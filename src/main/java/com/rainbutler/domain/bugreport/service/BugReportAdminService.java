package com.rainbutler.domain.bugreport.service;

import com.rainbutler.domain.bugreport.dto.AdminBugReportDetailResponse;
import com.rainbutler.domain.bugreport.dto.AdminBugReportResponse;
import com.rainbutler.domain.bugreport.dto.BugReportUpdateRequest;
import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import com.rainbutler.domain.bugreport.repository.BugReportRepository;
import com.rainbutler.domain.bugreport.repository.BugReportRepository.UserNickname;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.PageResponse;
import com.rainbutler.global.storage.StorageService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자의 버그 신고 조회 · 처리를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportAdminService {

    private final BugReportRepository bugReportRepository;
    private final StorageService storageService;
    private final Clock clock;

    /**
     * 신고 목록을 최신순으로 조회합니다.
     *
     * @param status 처리 상태 (null이면 전체)
     * @param category 분류 (null이면 전체)
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1~100)
     * @return 신고 페이지
     */
    public PageResponse<AdminBugReportResponse> search(BugReportStatus status,
            BugReportCategory category, int page, int size) {
        BugReportService.validatePage(page, size);
        Page<BugReport> result = bugReportRepository.search(status, category,
                PageRequest.of(page, size));
        // 신고자 닉네임을 한 번에 조회한다 (N+1 방지)
        Map<Long, String> nicknames = nicknames(result.getContent().stream()
                .map(BugReport::getUserId)
                .distinct()
                .toList());
        return PageResponse.of(result.getContent().stream()
                        .map(report -> AdminBugReportResponse.of(
                                report, nicknames.get(report.getUserId())))
                        .toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 신고 상세를 조회합니다.
     *
     * @param id 신고 id
     * @return 상세
     * @throws BusinessException 없는 신고 (BUG_REPORT_NOT_FOUND)
     */
    public AdminBugReportDetailResponse find(Long id) {
        return toDetail(get(id));
    }

    /**
     * 신고 상태 · 관리자 메모를 바꿉니다.
     *
     * @param id 신고 id
     * @param adminId 처리하는 관리자
     * @param request 바꿀 값 (보낸 것만)
     * @return 바뀐 상세
     * @throws BusinessException 없는 신고 (BUG_REPORT_NOT_FOUND)
     */
    @Transactional
    public AdminBugReportDetailResponse update(Long id, Long adminId,
            BugReportUpdateRequest request) {
        BugReport report = get(id);
        report.update(request.status(), request.adminNote(), adminId,
                LocalDateTime.now(clock));
        return toDetail(report);
    }

    private BugReport get(Long id) {
        return bugReportRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUG_REPORT_NOT_FOUND));
    }

    private AdminBugReportDetailResponse toDetail(BugReport report) {
        Map<Long, String> nicknames = nicknames(
                Stream.of(report.getUserId(), report.getResolvedBy())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        List<String> photoUrls = report.getPhotoKeys().stream()
                .map(storageService::toReadUrl)
                .toList();
        return AdminBugReportDetailResponse.of(report, photoUrls,
                nicknames.get(report.getUserId()),
                report.getResolvedBy() == null ? null : nicknames.get(report.getResolvedBy()));
    }

    /**
     * 사용자 id → 닉네임. 탈퇴한 사용자는 "탈퇴한 사용자"로 바꿉니다.
     */
    private Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (UserNickname user : bugReportRepository.findNicknames(userIds)) {
            result.put(user.getId(), Boolean.TRUE.equals(user.getWithdrawn())
                    ? User.WITHDRAWN_NICKNAME : user.getNickname());
        }
        return result;
    }
}
