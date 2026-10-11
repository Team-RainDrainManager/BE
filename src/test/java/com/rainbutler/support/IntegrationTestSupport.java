package com.rainbutler.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공통 설정입니다. PostgreSQL 16 컨테이너 하나를 모든 통합 테스트가 공유합니다.
 *
 * <p>컨테이너는 JVM이 끝날 때 Testcontainers가 정리합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }
}
