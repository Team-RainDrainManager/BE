package com.rainbutler.domain.user.repository;

import com.rainbutler.domain.user.entity.Provider;
import com.rainbutler.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * User 엔티티 저장소입니다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 로그인 방식과 이메일로 사용자를 찾습니다. (자체 로그인: provider = LOCAL)
     *
     * @param provider 로그인 방식
     * @param email 소문자로 정규화한 이메일
     * @return 사용자
     */
    Optional<User> findByProviderAndEmail(Provider provider, String email);

    /**
     * 로그인 방식과 이메일로 가입 여부를 확인합니다.
     *
     * @param provider 로그인 방식
     * @param email 소문자로 정규화한 이메일
     * @return 가입 여부
     */
    boolean existsByProviderAndEmail(Provider provider, String email);

    /**
     * 로그인 방식과 제공자 회원번호로 사용자를 찾습니다. (카카오: provider = KAKAO)
     *
     * @param provider 로그인 방식
     * @param providerId 제공자 회원번호
     * @return 사용자
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    /**
     * 온보딩을 마치고 알림을 켜 둔 사용자 id를 모두 찾습니다. (탈퇴 회원 제외)
     *
     * @return 알림을 받을 수 있는 사용자 id
     */
    @Query("SELECT u.id FROM User u "
            + "WHERE u.onboardingCompletedAt IS NOT NULL AND u.notificationEnabled = true")
    List<Long> findNotifiableUserIds();
}
