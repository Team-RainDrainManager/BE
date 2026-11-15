package com.rainbutler.domain.user.repository;

import com.rainbutler.domain.user.entity.Provider;
import com.rainbutler.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * 닉네임으로 가입 여부를 확인합니다. 대소문자는 구분하지 않습니다. (탈퇴한 회원은 세지 않는다)
     *
     * @param nickname 닉네임
     * @return 가입 여부
     */
    boolean existsByNicknameIgnoreCase(String nickname);

    /**
     * 다른 회원이 그 닉네임을 쓰고 있는지 확인합니다. 대소문자는 구분하지 않습니다.
     *
     * <p>내 닉네임을 그대로 다시 저장하는 것은 막지 않도록 {@code id}로 나를 뺍니다.
     *
     * @param nickname 닉네임
     * @param id 중복에서 뺄 회원 id (나)
     * @return 다른 회원이 쓰고 있으면 true
     */
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);
}
