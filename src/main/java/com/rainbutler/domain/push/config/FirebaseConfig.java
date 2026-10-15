package com.rainbutler.domain.push.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Firebase(FCM) 설정입니다.
 *
 * <p>자격 증명이 있으면 {@link FirebaseMessaging} 빈을 만들고, 없으면 빈을 만들지 않고 경고만 남깁니다.
 * 그래서 CI · 팀원 로컬에서는 키 없이도 앱이 뜨고, 푸시 발송만 생략됩니다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties properties;

    @PostConstruct
    void warnIfNotConfigured() {
        if (!properties.isConfigured()) {
            log.warn("Firebase 자격 증명(FIREBASE_CREDENTIALS_BASE64)이 설정되지 않아 푸시를 보내지 않습니다.");
        }
    }

    /**
     * FCM 발송 클라이언트입니다. 이미 초기화된 기본 FirebaseApp이 있으면 재사용합니다.
     *
     * @throws IllegalStateException 값은 있지만 Base64 · 서비스 계정 JSON 형식이 잘못된 경우
     */
    @Bean
    @Conditional(FirebaseConfiguredCondition.class)
    public FirebaseMessaging firebaseMessaging() {
        FirebaseApp app = FirebaseApp.getApps().stream()
                .filter(existing -> FirebaseApp.DEFAULT_APP_NAME.equals(existing.getName()))
                .findFirst()
                .orElseGet(this::initializeApp);
        return FirebaseMessaging.getInstance(app);
    }

    private FirebaseApp initializeApp() {
        try {
            byte[] json = Base64.getDecoder().decode(properties.credentialsBase64().strip());
            GoogleCredentials credentials =
                    GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            return FirebaseApp.initializeApp(
                    FirebaseOptions.builder().setCredentials(credentials).build());
        } catch (IllegalArgumentException | IOException e) {
            // 값이 있는데 형식이 틀리면 조용히 넘어가지 않고 시작을 실패시킨다
            throw new IllegalStateException(
                    "FIREBASE_CREDENTIALS_BASE64 형식이 올바르지 않습니다. "
                            + "서비스 계정 JSON을 base64 -i key.json | tr -d '\\n' 로 인코딩하세요.", e);
        }
    }

    /**
     * 자격 증명 값이 있을 때만 FCM 빈을 만듭니다.
     */
    static class FirebaseConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String value =
                    context.getEnvironment().getProperty("rain-butler.firebase.credentials-base64");
            return FirebaseProperties.isConfigured(value);
        }
    }
}
