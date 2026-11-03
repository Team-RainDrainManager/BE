package com.rainbutler.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공통 설정입니다. PostgreSQL 16 컨테이너 하나를 모든 통합 테스트가 공유합니다.
 *
 * <p>컨테이너는 JVM이 끝날 때 Testcontainers가 정리합니다.
 *
 * <p>컨테이너를 공유하므로 테스트가 커밋한 데이터가 다른 테스트에 남지 않도록,
 * 매 테스트 전에 빗물받이 관련 테이블을 비웁니다.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate integrationTestJdbcTemplate;

    /**
     * 부모 클래스의 @BeforeEach는 자식의 @BeforeEach보다 먼저 실행됩니다.
     * 자식의 setUp()이 만드는 데이터는 지워지지 않습니다.
     */
    @BeforeEach
    protected void cleanDrainTables() {
        integrationTestJdbcTemplate.execute(
                "TRUNCATE TABLE report, adoption, cleaning, drain RESTART IDENTITY CASCADE");
    }
}