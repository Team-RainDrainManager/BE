package com.rainbutler.domain.adoption.service;

import com.rainbutler.domain.adoption.dto.Request.AdoptionNicknameUpdateRequest;
import com.rainbutler.domain.adoption.dto.Request.AdoptionRegisterRequest;
import com.rainbutler.domain.adoption.dto.Request.AdoptionReleaseRequest;
import com.rainbutler.domain.adoption.dto.Response.AdoptionNicknameCheckResponse;
import com.rainbutler.domain.adoption.dto.Response.AdoptionNicknameUpdateResponse;
import com.rainbutler.domain.adoption.dto.Response.AdoptionRegisterResponse;
import com.rainbutler.domain.adoption.dto.Response.AdoptionReleaseResponse;
import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.adoption.entity.AdoptionStatus;
import com.rainbutler.domain.adoption.entity.ReleaseReason;
import com.rainbutler.domain.adoption.repository.AdoptionRepository;
import com.rainbutler.domain.cleaning.entity.Cleaning;
import com.rainbutler.domain.cleaning.entity.CleaningStatus;
import com.rainbutler.domain.cleaning.repository.CleaningRepository;
import com.rainbutler.domain.drain.entity.Drain;
import com.rainbutler.domain.drain.entity.DrainAdoptionStatus;
import com.rainbutler.domain.drain.repository.DrainRepository;
import com.rainbutler.domain.report.entity.ReportStatus;
import com.rainbutler.domain.report.repository.ReportRepository;
import com.rainbutler.domain.request.entity.InspectionRequest;
import com.rainbutler.domain.request.entity.RequestStatus;
import com.rainbutler.domain.request.repository.InspectionRequestRepository;
import com.rainbutler.domain.user.entity.User;
import com.rainbutler.domain.user.repository.UserRepository;
import com.rainbutler.global.entity.ReviewStatus;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdoptionService {

    private static final int MAX_ADOPTION_COUNT = 3;

    /** 입양을 막는 제보 상태 (반려 · 해제되면 풀린다) */
    private static final List<ReportStatus> BLOCKING_STATUSES =
            List.of(ReportStatus.PENDING, ReportStatus.APPROVED);

    private final AdoptionRepository adoptionRepository;
    private final DrainRepository drainRepository;
    private final CleaningRepository cleaningRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final ManagementCyclePolicy managementCyclePolicy;
    private final Clock clock;

    /**
     * 빗물받이를 입양합니다.
     *
     * <p>인증 통과한 청소를 통해 입양합니다. 배수구당 ACTIVE 입양은 1개만 가능하고, 별명은 입양 중인
     * 빗물받이 전체에서 겹치지 않아야 합니다. (대소문자 구분 없음)
     */
    @Transactional
    public AdoptionRegisterResponse registerAdoption(Long userId, Long drainId, AdoptionRegisterRequest request) {
        // 1. 안전 동의 확인
        if (!Boolean.TRUE.equals(request.safetyAgreed())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "안전 동의가 필요합니다.");
        }

        // 1-1. 별명 중복 확인 (입양 중인 빗물받이는 전체에서 별명이 겹치지 않는다)
        String nickname = normalizeNickname(request.nickname());
        if (adoptionRepository.existsByNicknameIgnoreCaseAndStatus(
                nickname, AdoptionStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        // 2. 빗물받이 존재 확인
        Drain drain = drainRepository.findById(drainId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRAIN_NOT_FOUND));

        // 2-1. AI가 청소 불가로 판정한 제보가 남아 있으면 입양할 수 없다
        if (reportRepository.existsByDrain_IdAndBlocksAdoptionTrueAndStatusIn(
                drainId, BLOCKING_STATUSES)) {
            throw new BusinessException(ErrorCode.DRAIN_ADOPTION_BLOCKED);
        }

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

        // 7-1. 촬영 위치가 불일치한 청소는 입양할 수 없다 (기존 데이터 방어)
        if (cleaning.isLocationMismatch()) {
            throw new BusinessException(ErrorCode.CLEANING_LOCATION_MISMATCH);
        }

        // 7-2. 관리자 검토 중이거나 반려된 청소는 입양할 수 없다
        if (cleaning.getReviewStatus() == ReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.CLEANING_UNDER_REVIEW);
        }
        if (cleaning.getReviewStatus() == ReviewStatus.REJECTED) {
            throw new BusinessException(ErrorCode.CLEANING_REJECTED);
        }

        // 8. 청소의 빗물받이가 요청한 빗물받이와 일치하는지 확인
        if (!cleaning.getDrain().getId().equals(drainId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "청소의 빗물받이와 일치하지 않습니다.");
        }

        // 9. 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 10. 입양 생성 (level=1, score=0 시작). 다음 관리 예정일은 입양일 + 2주 고정이다.
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime nextManagementAt = managementCyclePolicy.nextManagementAt(now, now);

        Adoption adoption = Adoption.builder()
                .user(user)
                .drain(drain)
                .cleaning(cleaning)
                .nickname(nickname)
                .adoptedAt(now)
                .build();

        // 입양 시점을 첫 관리로 본다 (다음 관리 예정일은 입양일 기준 14일 주기의 끝)
        adoption.recordManagement(now, nextManagementAt);

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

    /**
     * 입양한 빗물받이의 별명을 바꿉니다.
     *
     * <p>본인 입양만 바꿀 수 있고, 해제된 입양은 조회되지 않는 것과 같이 {@code ADOPTION_NOT_FOUND}로
     * 응답합니다. 별명 규칙(1~20자)은 입양 등록과 같습니다.
     *
     * @param userId 로그인 사용자 id
     * @param adoptionId 별명을 바꿀 입양 id
     * @param request 새 별명
     * @return 바뀐 별명과 수정 시각
     * @throws BusinessException 입양이 없거나 해제됐으면(404), 본인 입양이 아니면(403),
     *     다른 입양이 쓰는 별명이면(409)
     */
    @Transactional
    public AdoptionNicknameUpdateResponse updateNickname(
            Long userId, Long adoptionId, AdoptionNicknameUpdateRequest request) {
        Adoption adoption = adoptionRepository.findById(adoptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND));

        // 본인 입양만 바꿀 수 있다
        if (!adoption.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 해제된 입양은 상세 조회와 같은 기준으로 찾을 수 없는 입양으로 응답한다
        if (adoption.getStatus() != AdoptionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ADOPTION_NOT_FOUND);
        }

        // 바꾸려는 별명을 다른 입양이 쓰고 있으면 바꿀 수 없다 (본인 입양은 제외)
        String nickname = normalizeNickname(request.nickname());
        if (adoptionRepository.existsByNicknameIgnoreCaseAndStatusAndIdNot(
                nickname, AdoptionStatus.ACTIVE, adoptionId)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        adoption.changeNickname(nickname);
        // 수정 시각(Auditing)이 응답에 담기도록 먼저 반영한다
        adoptionRepository.flush();

        return AdoptionNicknameUpdateResponse.from(adoption);
    }

    /**
     * 입양 빗물받이 별명을 쓸 수 있는지 확인합니다.
     *
     * <p>입양 중(ACTIVE)인 빗물받이의 별명과 대소문자 구분 없이 비교합니다. 별명을 바꾸는 중이면
     * {@code excludeAdoptionId}로 자기 자신을 빼고 확인합니다.
     *
     * @param nickname 확인할 별명
     * @param excludeAdoptionId 중복에서 뺄 입양 id (등록할 때는 null)
     * @return 앞뒤 공백을 지운 별명과 사용 가능 여부
     */
    public AdoptionNicknameCheckResponse checkNickname(String nickname, Long excludeAdoptionId) {
        String normalized = normalizeNickname(nickname);
        boolean taken = excludeAdoptionId == null
                ? adoptionRepository.existsByNicknameIgnoreCaseAndStatus(
                        normalized, AdoptionStatus.ACTIVE)
                : adoptionRepository.existsByNicknameIgnoreCaseAndStatusAndIdNot(
                        normalized, AdoptionStatus.ACTIVE, excludeAdoptionId);
        return new AdoptionNicknameCheckResponse(normalized, !taken);
    }

    /**
     * 별명 앞뒤 공백을 지웁니다. 중복 확인과 저장이 같은 값으로 비교되게 합니다.
     */
    private static String normalizeNickname(String nickname) {
        return nickname == null ? null : nickname.strip();
    }

    /**
     * 입양을 해제합니다. (파양)
     *
     * <p>본인 입양만 해제할 수 있습니다. 해제된 입양은 삭제하지 않고 {@code RELEASED}로 남겨 기록 · 성장을
     * 보관하며, 빗물받이는 다시 입양할 수 있게 됩니다. 새 집사가 입양하면 새 입양이 Lv.1 · 0점으로 시작합니다.
     *
     * <p>이 입양에 걸린 {@code SENT} · {@code ACCEPTED} 점검 요청은 소유자 · 응답을 비우고 {@code OPEN}으로
     * 다시 공개해 근처 입양자가 대신 점검을 맡을 수 있게 합니다. ({@code CLAIMED} 요청은 그대로 둡니다.)
     *
     * @param userId 로그인 사용자 id
     * @param adoptionId 해제할 입양 id
     * @param request 파양 사유
     * @return 해제한 입양 id · 해제 시각
     * @throws BusinessException 입양이 없거나(404) 본인 입양이 아니거나(403) 이미 해제된 경우(409)
     */
    @Transactional
    public AdoptionReleaseResponse releaseAdoption(
            Long userId, Long adoptionId, AdoptionReleaseRequest request) {
        Adoption adoption = adoptionRepository.findById(adoptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADOPTION_NOT_FOUND));

        // 본인 입양만 해제할 수 있다
        if (!adoption.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 이미 해제된 입양은 다시 해제할 수 없다
        if (adoption.getStatus() == AdoptionStatus.RELEASED) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        release(adoption, request.reason().toReleaseReason(), now);

        return new AdoptionReleaseResponse(adoption.getId(), adoption.getReleasedAt());
    }

    /**
     * 입양을 해제하고, 이 입양에 걸린 {@code SENT} · {@code ACCEPTED} 요청을 다시 공개합니다.
     *
     * <p>직접 해제와 자동 해제가 같은 순서를 쓰도록 한 곳에 둡니다. 소유자를 비우기 전에 요청을 먼저 찾습니다.
     *
     * @param adoption 해제할 입양
     * @param reason 해제 사유
     * @param now 해제 시각
     */
    @Transactional
    public void release(Adoption adoption, ReleaseReason reason, LocalDateTime now) {
        List<InspectionRequest> requests = inspectionRequestRepository
                .findAllByAdoption_IdInAndStatusIn(
                        List.of(adoption.getId()), List.of(RequestStatus.SENT, RequestStatus.ACCEPTED));

        adoption.release(reason, now);
        requests.forEach(inspectionRequest -> inspectionRequest.reopenAfterRelease(now));
    }
}
