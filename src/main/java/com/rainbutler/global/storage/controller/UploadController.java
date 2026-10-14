package com.rainbutler.global.storage.controller;

import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.dto.PresignedUploadRequest;
import com.rainbutler.global.storage.dto.PresignedUploadResponse;
import com.rainbutler.global.swagger.ApiErrorCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사진 업로드 API입니다. 사진 바이트는 서버를 거치지 않고 S3로 바로 올라갑니다.
 */
@Tag(name = "Upload", description = "사진 업로드 (S3 presigned URL)")
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final String FLOW = """
            사진을 S3에 직접 올리는 URL을 발급합니다. (업로드 URL 5분, 최대 10MB, JPG · PNG · WEBP)

            **프론트 사용 흐름**
            1. 이 API로 `uploadUrl`과 `key`를 받습니다.
            2. `PUT uploadUrl` — body는 파일 바이너리, 헤더는 응답의 `headers`를 그대로 넣습니다.
               서버를 거치지 않습니다.
            3. 200이 오면 사진을 쓰는 API(빗물받이 등록, 청소 인증 등)에 **`key`를 보냅니다.**
            """;

    private final StorageService storageService;

    /**
     * 사진 업로드용 presigned URL을 발급합니다.
     */
    @Operation(summary = "사진 업로드 URL 발급", description = FLOW)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNSUPPORTED_IMAGE_TYPE,
            ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.STORAGE_NOT_CONFIGURED})
    @PostMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUploadResponse>> createPresignedUrl(
            @LoginUserId Long userId, @Valid @RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(PresignedUploadResponse.from(
                storageService.createUploadUrl(userId, request.purpose(), request.contentType()))));
    }
}
