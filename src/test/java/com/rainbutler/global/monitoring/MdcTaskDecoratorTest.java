package com.rainbutler.global.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.rainbutler.global.logging.LoggingConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 비동기 스레드로 요청 ID(MDC)가 이어지는지 검증합니다.
 */
class MdcTaskDecoratorTest {

    private final TaskDecorator decorator = new LoggingConfig().mdcTaskDecorator();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        MDC.clear();
        pool.shutdownNow();
    }

    @Test
    void 요청_스레드의_MDC를_작업_스레드로_복사하고_끝나면_되돌린다() throws Exception {
        MDC.put("requestId", "abcd1234");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> seen.set(MDC.get("requestId")));
        MDC.clear();

        CompletableFuture.runAsync(task, pool).get();
        AtomicReference<String> after = new AtomicReference<>("not-cleared");
        CompletableFuture.runAsync(() -> after.set(MDC.get("requestId")), pool).get();

        assertThat(seen.get()).isEqualTo("abcd1234");
        assertThat(after.get()).isNull();
    }
}
