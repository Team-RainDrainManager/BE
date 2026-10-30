package com.rainbutler.global.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ShedLock이 Flyway로 만든 shedlock 테이블로 잠금을 잡고, 잡힌 동안 같은 이름은 다시 잡지 못하는지 검증합니다.
 */
class ShedLockTest extends IntegrationTestSupport {

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_이름의_배치는_잠금이_풀릴_때까지_다시_잡지_못한다() {
        LockConfiguration config = new LockConfiguration(
                Instant.now(), "shedLockTest", Duration.ofMinutes(1), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(config);
        Optional<SimpleLock> second = lockProvider.lock(config);

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shedlock WHERE name = 'shedLockTest'", Integer.class))
                .isEqualTo(1);

        first.get().unlock();
        Optional<SimpleLock> afterUnlock = lockProvider.lock(config);
        assertThat(afterUnlock).isPresent();
        afterUnlock.get().unlock();
    }
}
