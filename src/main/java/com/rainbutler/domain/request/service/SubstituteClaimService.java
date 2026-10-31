package com.rainbutler.domain.request.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.request.dto.RequestClaimResponse;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.sanction.service.SanctionService;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.domain.user.service.OnboardingGuard;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.util.GeoDistance;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대신 점검 수락을 처리합니다. 누구나 수락할 수 있고, 입양권은 옮겨지지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class SubstituteClaimService {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final UserRepository userRepository;
    private final OnboardingGuard onboardingGuard;
    private final SanctionService sanctionService;
    private final PushService pushService;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 공개된 요청을 대신 점검으로 맡습니다.
     *
     * <ol>
     *   <li>온보딩 미완료 → 403 ONBOARDING_REQUIRED</li>
     *   <li>제재 중 → 403 USER_SANCTIONED</li>
     *   <li>없는 요청 → 404 REQUEST_NOT_FOUND</li>
     *   <li>그 요청의 원래 입양자 → 403 SUBSTITUTE_OWN_REQUEST</li>
     *   <li>현재 위치가 빗물받이에서 반경(300m) 밖 → 400 SUBSTITUTE_OUT_OF_RANGE</li>
     *   <li>오늘 이미 하루 제한(3건)만큼 수락 → 409 SUBSTITUTE_DAILY_LIMIT (수락 뒤 취소 · 놓침도 센다)</li>
     *   <li>이미 누가 맡음 → 409 REQUEST_ALREADY_CLAIMED, 그 밖에 OPEN이 아니거나 완료 마감 지남 → 409
     *       REQUEST_NOT_OPEN</li>
     *   <li>선착순: 조건부 UPDATE가 0행이면(동시에 다른 사람이 먼저 맡음) → 409 REQUEST_ALREADY_CLAIMED</li>
     * </ol>
     * 성공하면 원래 입양자에게 {@code REQUEST_CLAIMED} 알림을 보냅니다.
     *
     * @param userId 수락하는 사용자
     * @param requestId 요청 id
     * @param latitude 현재 위도 (저장하지 않음)
     * @param longitude 현재 경도 (저장하지 않음)
     * @return 수락 결과
     */
    @Transactional
    public RequestClaimResponse claim(Long userId, Long requestId, double latitude,
            double longitude) {
        onboardingGuard.require(userId);
        sanctionService.requireNotSanctioned(userId);
        InspectionRequest request = inspectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        Adoption adoption = request.getAdoption();
        if (adoption != null && adoption.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SUBSTITUTE_OWN_REQUEST);
        }
        Drain drain = request.getDrain();
        double distance = GeoDistance.meters(
                latitude, longitude, drain.getLatitude(), drain.getLongitude());
        if (distance > policy.substituteRadiusMeters()) {
            throw new BusinessException(ErrorCode.SUBSTITUTE_OUT_OF_RANGE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        long claimedToday = inspectionRequestRepository
                .countBySubstituteUser_IdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(
                        userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (claimedToday >= policy.substituteDailyLimit()) {
            throw new BusinessException(ErrorCode.SUBSTITUTE_DAILY_LIMIT);
        }
        if (request.getStatus() == RequestStatus.CLAIMED) {
            throw new BusinessException(ErrorCode.REQUEST_ALREADY_CLAIMED);
        }
        if (request.getStatus() != RequestStatus.OPEN
                || !now.isBefore(request.getCompleteDeadlineAt())) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_OPEN);
        }

        User substitute = userRepository.getReferenceById(userId);
        if (inspectionRequestRepository.claimIfOpen(requestId, substitute, now) == 0) {
            throw new BusinessException(ErrorCode.REQUEST_ALREADY_CLAIMED);
        }

        // 조건부 UPDATE가 영속성 컨텍스트를 비웠으므로 다시 읽는다
        InspectionRequest claimed = inspectionRequestRepository.findById(requestId).orElseThrow();
        Adoption original = claimed.getAdoption();
        if (original != null) {
            String nickname = userRepository.findById(userId).map(User::getNickname).orElse("이웃");
            pushService.notify(original.getUser().getId(), NotificationType.REQUEST_CLAIMED,
                    "대신 점검을 맡은 이웃이 생겼어요", nickname + "님이 대신 살펴봐 주기로 했어요.",
                    requestId, original.getId(), Map.of("requestId", String.valueOf(requestId)));
        }
        return RequestClaimResponse.from(claimed);
    }
}
