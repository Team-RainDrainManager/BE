package com.rainbutler.domain.photo.repository;

import com.rainbutler.domain.photo.entity.PhotoHash;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * PhotoHash 엔티티 저장소입니다. 쿼리 메서드는 도메인 담당자가 추가합니다.
 */
public interface PhotoHashRepository extends JpaRepository<PhotoHash, Long> {
}
