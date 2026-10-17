package com.rainbutler.domain.terms.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * classpath의 약관 전문(markdown)을 읽습니다. 한 번 읽은 전문은 메모리에 둡니다.
 *
 * <p>jar 안에서도 읽히도록 파일 경로가 아니라 InputStream으로 읽습니다.
 */
@Component
public class TermsContentLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 전문 파일이 classpath에 있는지 확인합니다.
     *
     * @param contentPath classpath 기준 경로 (예: terms/SERVICE_v1.md)
     * @return 존재 여부
     */
    public boolean exists(String contentPath) {
        return new ClassPathResource(contentPath).exists();
    }

    /**
     * 전문을 UTF-8 문자열로 읽습니다.
     *
     * @param contentPath classpath 기준 경로
     * @return 약관 전문 (markdown)
     */
    public String load(String contentPath) {
        return cache.computeIfAbsent(contentPath, path -> {
            try (InputStream input = new ClassPathResource(path).getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("약관 전문을 읽지 못했습니다: " + path, e);
            }
        });
    }
}
