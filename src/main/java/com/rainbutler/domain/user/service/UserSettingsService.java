package com.rainbutler.domain.user.service;

import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.entity.TermsType;
import com.rainbutler.domain.terms.entity.UserTermsAgreement;
import com.rainbutler.domain.terms.repository.TermsRepository;
import com.rainbutler.domain.terms.repository.UserTermsAgreementRepository;
import com.rainbutler.domain.user.dto.NotificationSettingsResponse;
import com.rainbutler.domain.user.dto.NotificationSettingsUpdateRequest;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정(활동 알림 · 야간 푸시)을 조회 · 변경합니다.
 *
 * <p>설정은 선택 약관(NOTIFICATION · NIGHT_PUSH) 동의와 같은 것이라, 값이 바뀔 때마다 동의 · 철회 이력을
 * 남깁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserRepository userRepository;
    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository agreementRepository;

    /**
     * 알림 설정을 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 알림 설정
     */
    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(Long userId) {
        return NotificationSettingsResponse.from(findUser(userId));
    }

    /**
     * 알림 설정을 바꿉니다. 보낸 값만 바꾸고, 실제로 바뀐 항목만 동의 이력에 새 행을 추가합니다.
     *
     * @param userId 로그인 사용자 id
     * @param request 바꿀 값 (null은 유지)
     * @return 바뀐 뒤 알림 설정
     * @throws BusinessException 둘 다 없음 (INVALID_INPUT) · 사용자 없음 (USER_NOT_FOUND)
     */
    @Transactional
    public NotificationSettingsResponse update(
            Long userId, NotificationSettingsUpdateRequest request) {
        if (request.notifications() == null && request.nightConsent() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "변경할 설정을 보내 주세요.");
        }
        User user = findUser(userId);
        boolean notificationChanged = request.notifications() != null
                && request.notifications() != user.isNotificationEnabled();
        boolean nightChanged = request.nightConsent() != null
                && request.nightConsent() != user.isNightPushConsent();

        user.updateNotificationSettings(request.notifications(), request.nightConsent());

        // 바뀐 항목만 동의 · 철회 이력을 남긴다 (같은 값이면 남기지 않음)
        if (notificationChanged || nightChanged) {
            LocalDateTime now = LocalDateTime.now();
            List<Terms> currentTerms = termsRepository.findCurrent(now);
            if (notificationChanged) {
                recordAgreement(user, currentTerms, TermsType.NOTIFICATION,
                        request.notifications(), now);
            }
            if (nightChanged) {
                recordAgreement(user, currentTerms, TermsType.NIGHT_PUSH,
                        request.nightConsent(), now);
            }
        }
        return NotificationSettingsResponse.from(user);
    }

    /**
     * 해당 약관의 현재 버전으로 동의(또는 철회) 이력을 추가합니다.
     */
    private void recordAgreement(
            User user, List<Terms> currentTerms, TermsType type, boolean agreed,
            LocalDateTime now) {
        currentTerms.stream()
                .filter(terms -> terms.getType() == type)
                .findFirst()
                .ifPresentOrElse(
                        terms -> agreementRepository.save(UserTermsAgreement.builder()
                                .user(user)
                                .terms(terms)
                                .agreed(agreed)
                                .agreedAt(now)
                                .build()),
                        () -> log.warn("[동의 이력 누락] 현재 {} 약관이 없습니다. userId={}",
                                type, user.getId()));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
