package com.rainbutler.domain.user.certificate;

import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.cleaning.entity.CleaningType;
import com.rainbutler.domain.inspection.entity.InspectionKind;
import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.ActivityStatsRepository;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.service.UserService;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 확인서를 발급합니다. 서버에 저장하지 않고 요청할 때마다 PDF를 만들어 바로 내려줍니다.
 *
 * <p>점검 수 · 활동 시작일은 점검({@code inspection})과 예전 기록(대신 점검 청소 · 입양 후 관리 기록)을 합산합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificateService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final ActivityStatsRepository activityStatsRepository;
    private final CertificatePdfRenderer renderer;
    private final Clock clock;

    /**
     * 로그인 사용자의 활동 확인서를 만듭니다.
     *
     * <p>정기 · 비 예보 · 대신 점검 수와 활동 시작일은 점검({@code inspection})과 예전 기록을 합산합니다.
     *
     * @param userId 로그인 사용자 id
     * @return PDF 바이트와 파일 이름
     * @throws BusinessException 사용자가 없거나(USER_NOT_FOUND), 실명을 등록하지 않았거나
     *     (IDENTITY_VERIFICATION_REQUIRED), 인정 기록이 없을 때 (CERTIFICATE_NO_ACTIVITY)
     */
    public IssuedCertificate issue(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRealName() == null || user.getRealName().isBlank()
                || !user.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED);
        }

        MeResponse me = userService.getMe(userId);
        if (me.inspectionCount() == 0) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NO_ACTIVITY);
        }

        // 종류별 점검 수: 점검(inspection) + 예전 기록. 셋의 합이 inspectionCount와 같다
        long regular = activityStatsRepository.countCompletedInspections(
                userId, List.of(InspectionKind.ROUTINE))
                + activityStatsRepository.countVerifiedManagementsByType(
                        userId, ManagementType.REGULAR);
        long forecast = activityStatsRepository.countCompletedInspections(
                userId, List.of(InspectionKind.FORECAST))
                + activityStatsRepository.countVerifiedManagementsByType(
                        userId, ManagementType.RAIN);
        long substitute = activityStatsRepository.countCompletedInspections(
                userId, List.of(InspectionKind.SUBSTITUTE))
                + activityStatsRepository.countCompletedCleanings(
                        userId, List.of(CleaningType.SUBSTITUTE));

        LocalDate issuedAt = LocalDate.now(clock);
        CertificateData data = new CertificateData(
                user.getRealName(),
                periodStart(userId, issuedAt),
                issuedAt,
                me.inspectionCount(),
                regular,
                forecast,
                substitute,
                me.volunteerMinutes());

        byte[] pdf = renderer.render(data);
        return new IssuedCertificate(pdf, "activity-certificate-" + issuedAt + ".pdf");
    }

    /**
     * 첫 인정 활동일을 구합니다. 점검 · 청소 · 입양 후 관리 기록 중 가장 이른 날짜를 씁니다.
     */
    private LocalDate periodStart(Long userId, LocalDate fallback) {
        LocalDateTime firstCleaning = activityStatsRepository.findFirstCleaningAt(
                userId, List.of(CleaningType.values()));
        LocalDateTime firstManagement = activityStatsRepository.findFirstManagementAt(
                userId, List.of(ManagementActionType.values()));
        LocalDateTime firstInspection = activityStatsRepository.findFirstInspectionAt(userId);
        LocalDateTime first = earliest(earliest(firstCleaning, firstManagement), firstInspection);
        return first == null ? fallback : first.toLocalDate();
    }

    private LocalDateTime earliest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }
}
