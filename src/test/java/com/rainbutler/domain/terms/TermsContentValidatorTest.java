package com.rainbutler.domain.terms;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.repository.TermsRepository;
import com.rainbutler.domain.terms.service.TermsContentLoader;
import com.rainbutler.domain.terms.service.TermsContentValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 시작 시 약관 전문 경로 검사를 검증합니다.
 */
class TermsContentValidatorTest {

    private final TermsRepository termsRepository = mock(TermsRepository.class);
    private final TermsContentValidator validator =
            new TermsContentValidator(termsRepository, new TermsContentLoader());

    @Test
    void 전문_파일이_모두_있으면_통과한다() {
        // 목 약관을 먼저 만든다 (stub 안에서 다른 stub을 만들면 Mockito가 거절한다)
        List<Terms> terms = List.of(terms("terms/SERVICE_v1.md"), terms(null));
        given(termsRepository.findAll()).willReturn(terms);

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void 전문_파일이_없으면_시작을_실패시킨다() {
        List<Terms> terms = List.of(terms("terms/SERVICE_v9.md"));
        given(termsRepository.findAll()).willReturn(terms);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terms/SERVICE_v9.md");
    }

    private static Terms terms(String contentPath) {
        Terms terms = mock(Terms.class);
        given(terms.hasContent()).willReturn(contentPath != null);
        given(terms.getContentPath()).willReturn(contentPath);
        return terms;
    }
}
