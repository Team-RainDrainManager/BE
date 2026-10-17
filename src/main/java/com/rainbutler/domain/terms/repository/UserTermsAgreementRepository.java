package com.rainbutler.domain.terms.repository;

import com.rainbutler.domain.terms.entity.UserTermsAgreement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * UserTermsAgreement 엔티티 저장소입니다.
 */
public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

    /**
     * 사용자의 동의 이력을 찾습니다.
     *
     * @param userId 사용자 id
     * @return 동의 이력
     */
    List<UserTermsAgreement> findAllByUser_Id(Long userId);
}
