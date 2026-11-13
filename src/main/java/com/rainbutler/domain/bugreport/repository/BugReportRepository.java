package com.rainbutler.domain.bugreport.repository;

import com.rainbutler.domain.bugreport.entity.BugReport;
import com.rainbutler.domain.bugreport.entity.BugReportCategory;
import com.rainbutler.domain.bugreport.entity.BugReportStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * BugReport 엔티티 저장소입니다.
 */
public interface BugReportRepository extends JpaRepository<BugReport, Long> {

    /**
     * 사용자가 기간 안에 신고한 수를 셉니다. (하루 제한)
     *
     * @param userId 신고자 id
     * @param from 시작 (포함)
     * @param to 끝 (제외)
     * @return 신고 수
     */
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, LocalDateTime from, LocalDateTime to);

    /**
     * 내 신고를 최신순으로 찾습니다.
     *
     * @param userId 신고자 id
     * @param pageable 페이지
     * @return 신고 페이지
     */
    Page<BugReport> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /**
     * 관리자 목록: 상태 · 분류로 거른 신고를 최신순으로 찾습니다. (null이면 그 조건 없음)
     *
     * @param status 처리 상태
     * @param category 분류
     * @param pageable 페이지
     * @return 신고 페이지
     */
    @Query("SELECT b FROM BugReport b WHERE (:status IS NULL OR b.status = :status) "
            + "AND (:category IS NULL OR b.category = :category) "
            + "ORDER BY b.createdAt DESC, b.id DESC")
    Page<BugReport> search(@Param("status") BugReportStatus status,
            @Param("category") BugReportCategory category, Pageable pageable);

    /**
     * 사용자 닉네임을 찾습니다. 탈퇴(소프트 삭제)한 사용자도 포함하려고 users를 직접 읽습니다.
     *
     * @param userIds 사용자 id 목록
     * @return id · 닉네임 · 탈퇴 여부
     */
    @Query(value = "SELECT u.id AS id, u.nickname AS nickname, "
            + "(u.deleted_at IS NOT NULL) AS withdrawn FROM users u WHERE u.id IN (:userIds)",
            nativeQuery = true)
    List<UserNickname> findNicknames(@Param("userIds") Collection<Long> userIds);

    /**
     * 닉네임 조회 결과입니다.
     */
    interface UserNickname {

        Long getId();

        String getNickname();

        Boolean getWithdrawn();
    }
}
