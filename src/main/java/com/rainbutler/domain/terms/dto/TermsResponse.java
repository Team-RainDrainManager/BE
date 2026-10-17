package com.rainbutler.domain.terms.dto;

import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.entity.TermsType;

/**
 * 약관 목록 항목입니다.
 *
 * @param termsId 약관 id (동의 요청에 보낼 값)
 * @param type 약관 종류
 * @param version 약관 버전
 * @param title 체크박스 문구 ([필수]/[선택]은 프론트가 required로 붙인다)
 * @param required 필수 동의 여부
 * @param hasContent 전문 유무 (true면 GET /api/v1/terms/{termsId}로 전문 조회)
 * @param displayOrder 표시 순서
 */
public record TermsResponse(
        Long termsId,
        TermsType type,
        Integer version,
        String title,
        boolean required,
        boolean hasContent,
        Integer displayOrder) {

    /**
     * 약관 엔티티로 응답을 만듭니다.
     */
    public static TermsResponse from(Terms terms) {
        return new TermsResponse(terms.getId(), terms.getType(), terms.getVersion(),
                terms.getTitle(), terms.isRequired(), terms.hasContent(), terms.getDisplayOrder());
    }
}
