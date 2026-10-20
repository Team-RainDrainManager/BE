package com.rainbutler.domain.user.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ReleaseReason;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.auth.repository.RefreshTokenRepository;
import com.rainbutler.domain.push.repository.FcmTokenRepository;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.toolbox.entity.RentalStatus;
import com.rainbutler.domain.toolbox.repository.ToolRentalRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴를 처리합니다. 소프트 삭제 + 개인정보 익명화이고, 활동 기록은 남깁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final ToolRentalRepository toolRentalRepository;
    private final AdoptionRepository adoptionRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final Clock clock;

    /**
     * 회원 탈퇴합니다. 한 트랜잭션으로 처리합니다.
     *
     * <ol>
     *   <li>대여 중인 도구가 있으면 막는다 (409 WITHDRAW_TOOL_NOT_RETURNED)</li>
     *   <li>입양을 모두 해제(사유 WITHDRAWN)하고 빗물받이를 입양 가능으로 돌린다</li>
     *   <li>해제한 입양의 SENT · ACCEPTED 요청과 이 사용자가 맡은 CLAIMED 요청을 대신 점검으로 다시 공개한다</li>
     *   <li>refresh 토큰 폐기 · FCM 토큰 삭제</li>
     *   <li>개인정보를 비우고 소프트 삭제한다</li>
     * </ol>
     *
     * @param userId 로그인 사용자 id
     * @throws BusinessException 사용자 없음 (USER_NOT_FOUND) · 도구 미반납 (WITHDRAW_TOOL_NOT_RETURNED)
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (toolRentalRepository.existsByUser_IdAndStatus(userId, RentalStatus.RENTED)) {
            throw new BusinessException(ErrorCode.WITHDRAW_TOOL_NOT_RETURNED);
        }
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 입양 해제 → 빗물받이 입양 가능
        List<Adoption> adoptions =
                adoptionRepository.findAllByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE);
        adoptions.forEach(adoption -> adoption.release(ReleaseReason.WITHDRAWN, now));

        // 2. 점검 요청 재공개 (입양자에게 간 요청 + 이 사용자가 맡은 대신 점검)
        List<Long> adoptionIds = adoptions.stream().map(Adoption::getId).toList();
        if (!adoptionIds.isEmpty()) {
            inspectionRequestRepository.findAllByAdoption_IdInAndStatusIn(adoptionIds,
                            List.of(RequestStatus.SENT, RequestStatus.ACCEPTED))
                    .forEach(request -> request.reopen(now));
        }
        inspectionRequestRepository.findAllBySubstituteUser_IdAndStatus(userId,
                        RequestStatus.CLAIMED)
                .forEach((InspectionRequest request) -> request.reopen(now));

        // 3. 인증 정리
        refreshTokenRepository.revokeAllByUserId(userId, now);
        fcmTokenRepository.deleteAllByUserId(userId);

        // 4. 익명화 + 소프트 삭제 (벌크 쿼리가 영속성 컨텍스트를 비웠을 수 있어 다시 읽는다)
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .withdraw(now);
        log.info("[회원 탈퇴] userId={} 입양 해제 {}건", userId, adoptions.size());
    }
}
