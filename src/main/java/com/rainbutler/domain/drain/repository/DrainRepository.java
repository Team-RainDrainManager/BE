package com.rainbutler.domain.drain.repository;

import com.rainbutler.domain.drain.entity.Drain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Drain 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface DrainRepository
        extends JpaRepository<Drain, Long>, JpaSpecificationExecutor<Drain> {
    List<Drain> findByLatitudeBetweenAndLongitudeBetween(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude
    );

    /**
     * 같은 사진으로 등록된 빗물받이가 있는지 확인합니다. (중복 생성 방지)
     *
     * @param photoUrl 현장 사진 URL
     * @return 존재 여부
     */
    boolean existsByPhotoUrl(String photoUrl);
}
