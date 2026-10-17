package com.rainbutler.domain.user.service;

import com.rainbutler.domain.terms.dto.TermsResponse;
import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.entity.TermsType;
import com.rainbutler.domain.terms.entity.UserTermsAgreement;
import com.rainbutler.domain.terms.repository.TermsRepository;
import com.rainbutler.domain.terms.repository.UserTermsAgreementRepository;
import com.rainbutler.domain.user.dto.OnboardingRequest;
import com.rainbutler.domain.user.dto.OnboardingStatusResponse;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.identity.IdentityVerifier;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩(약관 동의 + 본인인증 + 알림 설정)을 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final TermsRepository termsRepository;
    private final UserTermsAgreementRepository agreementRepository;
    private final IdentityVerifier identityVerifier;

    /**
     * 온보딩을 완료합니다. 동의 이력 저장 · 본인인증 · 알림 설정 · 완료 시각을 한 트랜잭션으로 처리합니다.
     *
     * <p>검증 순서: 이미 완료(409) → 모르는 · 중복 약관(400) → 옛 버전(400 TERMS_OUTDATED) → 현재 약관 누락(400)
     * → 필수 미동의(400) → 본인인증(400). 옛 버전을 누락보다 먼저 봐야 옛 id를 보낸 경우를 정확히 알려준다.
     *
     * @param userId 로그인 사용자 id
     * @param request 동의 목록 + 본인인증 여부
     * @return 완료 후 온보딩 상태
     */
    @Transactional
    public OnboardingStatusResponse complete(Long userId, OnboardingRequest request) {
        User user = findUser(userId);
        if (user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Terms> currentTerms = termsRepository.findCurrent(now);
        Map<Long, Boolean> answers = toAnswers(request);
        Map<Long, Terms> answeredTerms = termsRepository.findAllById(answers.keySet()).stream()
                .collect(Collectors.toMap(Terms::getId, Function.identity()));

        // 모르는 약관 id
        if (answeredTerms.size() != answers.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 약관이 있어요.");
        }
        // 옛 버전 약관 id (현재 약관이 아닌 id)
        Set<Long> currentIds = currentTerms.stream().map(Terms::getId).collect(Collectors.toSet());
        if (!currentIds.containsAll(answers.keySet())) {
            throw new BusinessException(ErrorCode.TERMS_OUTDATED);
        }
        // 현재 약관 중 응답이 빠진 것
        if (!answers.keySet().containsAll(currentIds)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "모든 약관에 대한 동의 여부를 보내 주세요.");
        }
        // 필수 약관 미동의
        boolean requiredRejected = currentTerms.stream()
                .anyMatch(terms -> terms.isRequired() && !answers.get(terms.getId()));
        if (requiredRejected) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        if (!Boolean.TRUE.equals(request.identityVerified()) || !identityVerifier.verify(userId)) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_REQUIRED_ON_ONBOARDING);
        }

        // 1. 동의 이력 (선택 약관 미동의도 false로 남긴다)
        for (Terms terms : currentTerms) {
            agreementRepository.save(UserTermsAgreement.builder()
                    .user(user)
                    .terms(terms)
                    .agreed(answers.get(terms.getId()))
                    .agreedAt(now)
                    .build());
        }
        // 2. 본인인증 (인증 시각만 기록, 개인정보 저장 없음)
        user.markIdentityVerified(now);
        // 3. 선택 약관 동의값 → 알림 설정, 4. 완료 시각
        user.completeOnboarding(now,
                agreedTo(currentTerms, answers, TermsType.NOTIFICATION),
                agreedTo(currentTerms, answers, TermsType.NIGHT_PUSH));

        Set<Long> agreedIds = answers.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return status(user, currentTerms, agreedIds);
    }

    /**
     * 온보딩 상태를 조회합니다.
     *
     * @param userId 로그인 사용자 id
     * @return 온보딩 상태
     */
    @Transactional(readOnly = true)
    public OnboardingStatusResponse getStatus(Long userId) {
        User user = findUser(userId);
        List<Terms> currentTerms = termsRepository.findCurrent(LocalDateTime.now());
        Set<Long> agreedIds = agreementRepository.findAllByUser_Id(userId).stream()
                .filter(UserTermsAgreement::isAgreed)
                .map(agreement -> agreement.getTerms().getId())
                .collect(Collectors.toSet());
        return status(user, currentTerms, agreedIds);
    }

    /**
     * 상태 응답을 만듭니다. 동의한 적 없는 현재 필수 약관(새 버전 포함)을 pending으로 내려준다.
     */
    private OnboardingStatusResponse status(
            User user, List<Terms> currentTerms, Set<Long> agreedIds) {
        List<TermsResponse> pending = currentTerms.stream()
                .filter(Terms::isRequired)
                .filter(terms -> !agreedIds.contains(terms.getId()))
                .map(TermsResponse::from)
                .toList();
        return new OnboardingStatusResponse(user.isOnboardingCompleted(),
                user.getOnboardingCompletedAt(), user.isIdentityVerified(), pending);
    }

    /**
     * 요청을 약관 id → 동의 여부로 바꿉니다. 같은 약관이 두 번 오면 거절합니다.
     */
    private static Map<Long, Boolean> toAnswers(OnboardingRequest request) {
        Map<Long, Boolean> answers = new HashMap<>();
        for (OnboardingRequest.Agreement agreement : request.agreements()) {
            if (answers.put(agreement.termsId(), agreement.agreed()) != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 약관이 두 번 들어 있어요.");
            }
        }
        return answers;
    }

    /**
     * 현재 약관 중 해당 종류에 동의했는지 확인합니다. (선택 약관 → 알림 설정)
     */
    private static boolean agreedTo(
            List<Terms> currentTerms, Map<Long, Boolean> answers, TermsType type) {
        return currentTerms.stream()
                .filter(terms -> terms.getType() == type)
                .anyMatch(terms -> answers.get(terms.getId()));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
