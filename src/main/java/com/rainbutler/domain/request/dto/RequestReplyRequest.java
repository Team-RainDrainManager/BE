package com.rainbutler.domain.request.dto;

import com.rainbutler.domain.request.entity.RequestReply;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 비 예보 요청 가능 · 불가 응답입니다.
 *
 * @param reply ACCEPT(가능) · DECLINE(불가)
 */
public record RequestReplyRequest(@Schema(example = "ACCEPT") @NotNull RequestReply reply) {
}
