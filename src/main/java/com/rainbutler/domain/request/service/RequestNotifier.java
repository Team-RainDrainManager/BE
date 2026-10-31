package com.rainbutler.domain.request.service;

import com.rainbutler.domain.adoption.entity.Adoption;
import com.rainbutler.domain.notification.entity.NotificationType;
import com.rainbutler.domain.push.service.PushService;
import com.rainbutler.domain.request.entity.InspectionRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 점검 요청 관련자(입양자, 대신 점검 수락자)에게 같은 알림을 보냅니다.
 */
@Component
@RequiredArgsConstructor
public class RequestNotifier {

    private final PushService pushService;

    /**
     * 요청의 입양자와 대신 점검 수락자에게 알립니다. 호출하는 쪽 트랜잭션 안에서 부르세요.
     *
     * @param request 요청
     * @param type 알림 종류
     * @param title 제목
     * @param body 내용
     */
    public void notifyRelated(InspectionRequest request, NotificationType type, String title,
            String body) {
        Map<String, String> data = Map.of("requestId", String.valueOf(request.getId()));
        Adoption adoption = request.getAdoption();
        if (adoption != null) {
            pushService.notify(adoption.getUser().getId(), type, title, body, request.getId(),
                    adoption.getId(), data);
        }
        if (request.getSubstituteUser() != null) {
            pushService.notify(request.getSubstituteUser().getId(), type, title, body,
                    request.getId(), null, data);
        }
    }
}
