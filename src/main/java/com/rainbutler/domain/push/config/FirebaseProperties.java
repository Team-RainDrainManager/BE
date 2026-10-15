package com.rainbutler.domain.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase(FCM) 설정입니다. ({@code rain-butler.firebase})
 *
 * @param credentialsBase64 서비스 계정 JSON을 Base64 한 줄로 인코딩한 값 (비어 있으면 푸시 발송 생략)
 */
@ConfigurationProperties(prefix = "rain-butler.firebase")
public record FirebaseProperties(String credentialsBase64) {

    /**
     * 자격 증명이 설정됐는지 확인합니다. 환경변수가 없으면 "${...}" 문자열이 들어올 수 있어 함께 막습니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return isConfigured(credentialsBase64);
    }

    static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !value.startsWith("${");
    }
}
