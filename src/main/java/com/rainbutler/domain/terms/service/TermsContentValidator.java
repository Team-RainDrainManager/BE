package com.rainbutler.domain.terms.service;

import com.rainbutler.domain.terms.entity.Terms;
import com.rainbutler.domain.terms.repository.TermsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 앱 시작 시 약관의 전문 경로({@code content_path})가 모두 classpath에 있는지 검사합니다.
 *
 * <p>시드 · 마이그레이션의 경로 오타로 전문이 빠진 채 배포되지 않도록, 하나라도 없으면 시작을 실패시킵니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TermsContentValidator implements ApplicationRunner {

    private final TermsRepository termsRepository;
    private final TermsContentLoader termsContentLoader;

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = termsRepository.findAll().stream()
                .filter(Terms::hasContent)
                .map(Terms::getContentPath)
                .filter(path -> !termsContentLoader.exists(path))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "약관 전문 파일이 classpath에 없습니다: " + missing
                            + " (src/main/resources/ 아래 경로와 terms.content_path를 확인하세요)");
        }
        log.info("약관 전문 경로 검사 완료");
    }
}
