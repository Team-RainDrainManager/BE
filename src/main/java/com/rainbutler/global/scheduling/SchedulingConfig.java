package com.rainbutler.global.scheduling;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 스케줄러 설정입니다.
 *
 * <p>배포 중에는 새 · 옛 컨테이너가 잠깐 함께 뜰 수 있어서, ShedLock(DB {@code shedlock} 테이블)으로 같은 배치가
 * 동시에 두 번 돌지 않게 막습니다. 배치 메서드에는 {@code @SchedulerLock(name = ...)}을 붙입니다.
 *
 * <p>모든 cron은 {@code zone = "Asia/Seoul"}로 적습니다. 테스트 프로필은
 * {@code rain-butler.scheduling.enabled=false}라 배치가 스스로 돌지 않고, 테스트가 배치 메서드를 직접 부릅니다.
 *
 * <p>스레드 풀(2개)은 {@code spring.task.scheduling}으로 설정합니다. 스케줄러 빈을 직접 만들면 Boot가
 * {@code @Async}용 실행기를 만들지 않아 푸시 발송이 배치 스레드를 같이 쓰게 되기 때문입니다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    /**
     * ShedLock 잠금 저장소입니다. 잠금 시각은 서버마다 시계가 달라도 어긋나지 않게 DB 시각을 씁니다.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
