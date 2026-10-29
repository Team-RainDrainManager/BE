package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.dto.Request.AdoptionRegisterRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionRegisterResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.entity.CleaningStatus;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdoptionService {

    private static final int MAX_ADOPTION_COUNT = 3;
    private static final int MANAGEMENT_INTERVAL_DAYS = 14;

    private final AdoptionRepository adoptionRepository;
    private final DrainRepository drainRepository;
    private final CleaningRepository cleaningRepository;
    private final UserRepository userRepository;

    /**
     * 빗물받이를 입양합니다.
     *
     * <p>인증 통과한 청소를 통해 입양합니다. 배수구당 ACTIVE 입양은 1개만 가능합니다.
     */
    @Transactional
    public AdoptionRegisterResponse registerAdoption(Long userId, Long drainId, AdoptionRegisterRequest request) {
        // 1. 안전 동의 확인
        if (!Boolean.TRUE.equals(request.safetyAgreed())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "안전 동의가 필요합니다.");
        }

        // 2. 빗물받이 존재 확인
        Drain drain = drainRepository.findById(drainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAIN_NOT_FOUND));

        // 3. 빗물받이 입양 상태 확인
        if (drain.getAdoptionStatus() == DrainAdoptionStatus.ADOPTED) {
            throw new BusinessException(ErrorCode.DRAIN_ALREADY_ADOPTED);
        }

        // 4. 사용자 입양 수 제한 확인
        long adoptionCount = adoptionRepository.countByUser_IdAndStatus(userId, AdoptionStatus.ACTIVE);
        if (adoptionCount >= MAX_ADOPTION_COUNT) {
            throw new BusinessException(ErrorCode.ADOPTION_LIMIT_EXCEEDED);
        }

        // 5. 청소 존재 및 상태 확인
        Cleaning cleaning = cleaningRepository.findById(request.cleaningId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "청소를 찾을 수 없습니다."));

        // 6. 본인의 청소인지 확인
        if (!cleaning.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 7. 청소 완료 상태 확인
        if (cleaning.getStatus() != CleaningStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인증에 실패한 청소입니다.");
        }

        // 8. 청소의 빗물받이가 요청한 빗물받이와 일치하는지 확인
        if (!cleaning.getDrain().getId().equals(drainId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "청소의 빗물받이와 일치하지 않습니다.");
        }

        // 9. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 10. 입양 생성 (level=1, score=0 시작)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextManagementAt = now.plusDays(MANAGEMENT_INTERVAL_DAYS);

        Adoption adoption = Adoption.builder()
                .user(user)
                .drain(drain)
                .cleaning(cleaning)
                .nickname(request.nickname())
                .adoptedAt(now)
                .build();

        // builder에 없는 필드 설정
        adoption.setLastManagedAt(now);
        adoption.setNextManagementAt(nextManagementAt);

        Adoption saved = adoptionRepository.save(adoption);

        // 11. 빗물받이 입양 상태 변경
        drain.setAdoptionStatus(DrainAdoptionStatus.ADOPTED);
        drainRepository.save(drain);

        return new AdoptionRegisterResponse(
                saved.getId(),
                drainId,
                saved.getNickname(),
                saved.getAdoptedAt(),
                saved.getNextManagementAt()
        );
    }
}
