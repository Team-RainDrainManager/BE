package com.rainbutler.domain.push.repository;

import com.rainbutler.domain.push.entity.FcmToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * FcmToken 엔티티 저장소입니다.
 */
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    /**
     * 토큰 값으로 찾습니다.
     *
     * @param token FCM 토큰
     * @return 토큰
     */
    Optional<FcmToken> findByToken(String token);

    /**
     * 사용자의 모든 기기 토큰을 찾습니다.
     *
     * @param userId 사용자 id
     * @return 토큰 목록
     */
    List<FcmToken> findAllByUser_Id(Long userId);

    /**
     * 무효 토큰을 한 번에 하드 삭제합니다.
     *
     * @param tokens 삭제할 토큰 값
     * @return 삭제한 수
     */
    @Modifying
    @Query("DELETE FROM FcmToken t WHERE t.token IN :tokens")
    int deleteAllByTokenIn(@Param("tokens") Collection<String> tokens);

    /**
     * 사용자의 기기 토큰을 전부 하드 삭제합니다. (회원 탈퇴)
     *
     * @param userId 사용자 id
     * @return 삭제한 수
     */
    @Modifying
    @Query("DELETE FROM FcmToken t WHERE t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
