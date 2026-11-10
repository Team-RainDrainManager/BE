package com.rainbutler.domain.request.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.request.dto.RequestReplyResponse;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestReply;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.user.service.OnboardingGuard;
import com.rainbutler.global.config.PolicyProperties;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입양자의 비 예보 요청 가능 · 불가 응답을 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class RequestReplyService {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final OnboardingGuard onboardingGuard;
    private final NearbyHelpCounter nearbyHelpCounter;
    private final PolicyProperties policy;
    private final Clock clock;

    /**
     * 가능 · 불가로 응답합니다.
     *
     * <ul>
     *   <li>가능 → ACCEPTED, 완료 마감과 근처(기본 100m) 도움 필요 빗물받이 수를 함께 돌려준다</li>
     *   <li>불가 → OPEN (대신 점검 공개, 지도에 표시. 근처 입양자에게 개별 알림은 보내지 않는다)</li>
     *   <li>어느 쪽이든 연속 무응답 횟수 초기화</li>
     * </ul>
     *
     * @param userId 응답하는 사용자 (요청의 입양자)
     * @param requestId 요청 id
     * @param reply 가능 · 불가
     * @return 바뀐 상태
     * @throws BusinessException 온보딩 미완료(403) · 없는 요청(404) · 입양자 아님(403) ·
     *     응답할 수 없는 상태나 마감 지남(409)
     */
    @Transactional
    public RequestReplyResponse reply(Long userId, Long requestId, RequestReply reply) {
        onboardingGuard.require(userId);
        InspectionRequest request = inspectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        Adoption adoption = request.getAdoption();
        if (adoption == null || !adoption.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.getStatus() != RequestStatus.SENT
                || !now.isBefore(request.getReplyDeadlineAt())) {
            throw new BusinessException(ErrorCode.REQUEST_REPLY_NOT_ALLOWED);
        }

        adoption.resetIgnoreCount();
        if (reply == RequestReply.ACCEPT) {
            request.accept(now);
            // 가능이면 근처에서 도움이 필요한 빗물받이 수를 알려 같이 둘러보게 한다 (추가 알림 없음)
            return RequestReplyResponse.of(request,
                    nearbyHelpCounter.countOpenNear(request.getDrain(), now),
                    policy.nearbyHelpRadiusMeters());
        }
        request.decline(now);
        return RequestReplyResponse.of(request, null, null);
    }
}
