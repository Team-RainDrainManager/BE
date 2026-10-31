package com.rainbutler.global.scheduling;

import com.rainbutler.global.config.PolicyProperties;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정책 시각({@code rain-butler.policy})으로 매일 도는 배치의 cron을 만듭니다.
 *
 * <p>시각을 cron에 따로 적지 않고 정책 값 하나만 바꾸면 되게 합니다.
 * 사용: {@code @Scheduled(cron = "#{@scheduleCrons.rainNotice()}", zone = "Asia/Seoul")}
 */
@Component("scheduleCrons")
@RequiredArgsConstructor
public class ScheduleCrons {

    private final PolicyProperties policy;

    /**
     * 비 예보 요청 일괄 발송 (기본 매일 06:00)
     *
     * @return cron
     */
    public String rainNotice() {
        return daily(policy.rainNoticeTime());
    }

    /**
     * 야간 푸시 미동의자 푸시 (기본 매일 09:00)
     *
     * @return cron
     */
    public String nightDeferredPush() {
        return daily(policy.nightDeferredPushTime());
    }

    private static String daily(LocalTime time) {
        return "0 " + time.getMinute() + " " + time.getHour() + " * * *";
    }
}
