package com.rainbutler.domain.terms.dto;

import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.entity.TermsType;

/**
 * 약관 전문 응답입니다.
 *
 * @param termsId 약관 id
 * @param type 약관 종류
 * @param version 약관 버전
 * @param title 체크박스 문구
 * @param required 필수 동의 여부
 * @param content 약관 전문 (markdown). 전문이 없는 약관(AGE_14)은 null
 */
public record TermsContentResponse(
        Long termsId,
        TermsType type,
        Integer version,
        String title,
        boolean required,
        String content) {

    /**
     * 약관 엔티티와 전문으로 응답을 만듭니다.
     */
    public static TermsContentResponse of(Terms terms, String content) {
        return new TermsContentResponse(terms.getId(), terms.getType(), terms.getVersion(),
                terms.getTitle(), terms.isRequired(), content);
    }
}
