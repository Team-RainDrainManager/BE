package com.rainbutler.domain.auth.repository;

import com.rainbutler.domain.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * RefreshToken 엔티티 저장소입니다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 토큰 해시로 refresh 토큰을 찾습니다.
     *
     * @param tokenHash refresh 토큰 SHA-256
     * @return refresh 토큰
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 사용자의 살아 있는 refresh 토큰을 전부 폐기합니다. (탈취 감지 시)
     *
     * <p>벌크 업데이트는 Auditing이 동작하지 않으므로 updated_at도 직접 갱신합니다.
     *
     * @param userId 사용자 id
     * @param now 폐기 시각
     * @return 폐기한 토큰 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now, r.updatedAt = :now "
            + "WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
