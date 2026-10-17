package com.rainbutler.domain.terms.service;

import com.rainbutler.domain.terms.dto.TermsContentResponse;
import com.rainbutler.domain.terms.dto.TermsResponse;
import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.repository.TermsRepository;
import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 목록 · 전문을 조회합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

    private final TermsRepository termsRepository;
    private final TermsContentLoader termsContentLoader;

    /**
     * 현재 약관 목록을 표시 순서대로 조회합니다. (type별로 시행 중인 최신 버전)
     *
     * @return 현재 약관 목록
     */
    public List<TermsResponse> getCurrentTerms() {
        return termsRepository.findCurrent(LocalDateTime.now()).stream()
                .map(TermsResponse::from)
                .toList();
    }

    /**
     * 약관 전문을 조회합니다. 전문이 없는 약관은 content가 null입니다.
     *
     * @param termsId 약관 id
     * @return 약관 전문
     * @throws BusinessException 없는 약관 (TERMS_NOT_FOUND)
     */
    public TermsContentResponse getContent(Long termsId) {
        Terms terms = termsRepository.findById(termsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERMS_NOT_FOUND));
        String content = terms.hasContent()
                ? termsContentLoader.load(terms.getContentPath()) : null;
        return TermsContentResponse.of(terms, content);
    }
}
