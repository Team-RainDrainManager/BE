package com.rainbutler.domain.bugreport.service;

import com.rainbutler.domain.bugreport.dto.BugReportCreateRequest;
import com.rainbutler.domain.bugreport.dto.BugReportCreateResponse;
import com.rainbutler.domain.bugreport.dto.MyBugReportResponse;
import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.repository.BugReportRepository;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.PageResponse;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 앱 버그 신고를 처리합니다.
 *
 * <p>버그 때문에 온보딩을 끝내지 못했을 수도 있어 온보딩 · 제재 검사는 하지 않습니다. 신고 내용은 개인정보가 섞일 수
 * 있어 로그에 남기지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportService {

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 100;

    private final BugReportRepository bugReportRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 버그를 신고합니다.
     *
     * @param userId 신고자
     * @param request 신고 내용
     * @param userAgent 요청 헤더 User-Agent (500자에서 자른다)
     * @return 등록 결과
     * @throws BusinessException 내용 10자 미만(400) · 없는 사용자(404) · 하루 제한 초과(429) · 사진 검증 실패
     */
    @Transactional
    public BugReportCreateResponse create(Long userId, BugReportCreateRequest request,
            String userAgent) {
        String content = request.content().strip();
        if (content.length() < MIN_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "내용을 " + MIN_CONTENT_LENGTH + "자 이상 적어 주세요.");
        }
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        LocalDate today = LocalDate.now(clock);
        long todayCount = bugReportRepository
                .countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (todayCount >= policy.bugReportDailyLimit()) {
            throw new BusinessException(ErrorCode.BUG_REPORT_DAILY_LIMIT);
        }
        List<String> photoKeys = request.photoKeys() == null ? List.of()
                : request.photoKeys().stream()
                        .map(key -> storageService.verifyUploaded(
                                userId, key, UploadPurpose.BUG_REPORT))
                        .toList();

        BugReport report = bugReportRepository.save(BugReport.builder()
                .userId(userId)
                .category(request.category())
                .content(content)
                .screen(blankToNull(request.screen()))
                .requestId(blankToNull(request.requestId()))
                .appVersion(blankToNull(request.appVersion()))
                .platform(request.platform())
                .userAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH))
                .photoKeys(photoKeys)
                .build());
        // Grafana 로그에서 바로 보이게 WARN으로 남긴다. 내용 본문은 남기지 않는다
        log.warn("[버그 신고] id={} category={} userId={} requestId={}", report.getId(),
                report.getCategory(), userId, report.getRequestId());
        return BugReportCreateResponse.from(report);
    }

    /**
     * 내 신고를 최신순으로 조회합니다.
     *
     * @param userId 신고자
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1~100)
     * @return 신고 페이지
     */
    public PageResponse<MyBugReportResponse> findMine(Long userId, int page, int size) {
        validatePage(page, size);
        Page<BugReport> result = bugReportRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                userId, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(MyBugReportResponse::from)
                        .toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 페이지 번호 · 크기를 검사합니다.
     *
     * @throws BusinessException 범위를 벗어남 (INVALID_INPUT)
     */
    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 해요.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
