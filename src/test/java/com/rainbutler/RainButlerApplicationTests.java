package com.rainbutler;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 PostgreSQL 16 컨테이너로 애플리케이션 컨텍스트를 띄웁니다.
 *
 * <p>컨텍스트가 뜨면 Flyway 마이그레이션과 JPA {@code ddl-auto: validate}(엔티티 ↔ 테이블 검사)가 모두
 * 성공한 것입니다.
 */
class RainButlerApplicationTests extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // Flyway 이력 테이블을 뺀 업무 테이블이 19개 만들어졌는지 확인
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tableCount).isEqualTo(19);
    }
}
