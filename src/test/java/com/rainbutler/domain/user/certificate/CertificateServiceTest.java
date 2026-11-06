package com.rainbutler.domain.user.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rainbutler.domain.adoption.entity.ManagementActionType;
import com.rainbutler.domain.adoption.entity.ManagementType;
import com.rainbutler.domain.cleaning.entity.CleaningType;
import com.rainbutler.domain.user.dto.MeResponse;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.ActivityStatsRepository;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.service.UserService;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 활동 확인서 발급 조건과 PDF에 넘기는 값을 확인합니다.
 */
class CertificateServiceTest {

    private static final long USER_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final ActivityStatsRepository activityStatsRepository =
            mock(ActivityStatsRepository.class);
    private final CertificatePdfRenderer renderer = mock(CertificatePdfRenderer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), SEOUL);

    private CertificateService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CertificateService(
                userRepository, userService, activityStatsRepository, renderer, clock);
        user = User.local("a@test.com", "{noop}x", "또랑이");
        user.verifyIdentity("홍길동", LocalDateTime.of(2026, 10, 1, 12, 0));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(renderer.render(org.mockito.ArgumentMatchers.any()))
                .willReturn("%PDF-test".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void 실명과_활동_기록으로_확인서를_만든다() {
        MeResponse me = mock(MeResponse.class);
        given(me.inspectionCount()).willReturn(24L);
        given(me.volunteerMinutes()).willReturn(360L);
        given(userService.getMe(USER_ID)).willReturn(me);

        given(activityStatsRepository.countVerifiedManagementsByType(
                USER_ID, ManagementType.REGULAR)).willReturn(18L);
        given(activityStatsRepository.countVerifiedManagementsByType(
                USER_ID, ManagementType.RAIN)).willReturn(4L);
        given(activityStatsRepository.countCompletedCleanings(
                USER_ID, java.util.List.of(CleaningType.SUBSTITUTE))).willReturn(2L);
        given(activityStatsRepository.findFirstCleaningAt(
                USER_ID, java.util.List.of(CleaningType.values())))
                .willReturn(LocalDateTime.of(2026, 3, 2, 10, 0));
        given(activityStatsRepository.findFirstManagementAt(
                USER_ID, java.util.List.of(ManagementActionType.values())))
                .willReturn(LocalDateTime.of(2026, 4, 1, 10, 0));

        IssuedCertificate issued = service.issue(USER_ID);

        assertThat(issued.filename()).isEqualTo("activity-certificate-2026-11-05.pdf");
        assertThat(issued.content()).isEqualTo("%PDF-test".getBytes(StandardCharsets.ISO_8859_1));

        ArgumentCaptor<CertificateData> captor = ArgumentCaptor.forClass(CertificateData.class);
        verify(renderer).render(captor.capture());
        CertificateData data = captor.getValue();
        assertThat(data.realName()).isEqualTo("홍길동");
        assertThat(data.periodStart()).isEqualTo(java.time.LocalDate.of(2026, 3, 2));
        assertThat(data.issuedAt()).isEqualTo(java.time.LocalDate.of(2026, 11, 5));
        assertThat(data.inspectionCount()).isEqualTo(24);
        assertThat(data.regularCount()).isEqualTo(18);
        assertThat(data.forecastCount()).isEqualTo(4);
        assertThat(data.substituteCount()).isEqualTo(2);
        assertThat(data.volunteerMinutes()).isEqualTo(360);
    }

    @Test
    void 실명을_등록하지_않으면_발급할_수_없다() {
        user = User.local("a@test.com", "{noop}x", "또랑이");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.issue(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.IDENTITY_VERIFICATION_REQUIRED));
    }

    @Test
    void 인정_기록이_없으면_발급할_수_없다() {
        MeResponse me = mock(MeResponse.class);
        given(me.inspectionCount()).willReturn(0L);
        given(userService.getMe(USER_ID)).willReturn(me);

        assertThatThrownBy(() -> service.issue(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode())
                                .isEqualTo(ErrorCode.CERTIFICATE_NO_ACTIVITY));
    }
}
