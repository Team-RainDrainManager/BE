package com.rainbutler.domain.photo.controller;

import com.rainbutler.domain.photo.dto.PhotoPresignRequest;
import com.rainbutler.domain.photo.dto.PhotoPresignResponse;
import com.rainbutler.global.exception.ErrorCode;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.security.LoginUserId;
import com.rainbutler.global.storage.StorageService;
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

/** 점검·청소·제보 사진의 S3 직접 업로드 API입니다. */
@Tag(name = "Photo", description = "사진 업로드 (S3 presigned URL)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private static final String PRESIGN_DESCRIPTION = """
            사진을 서버에 전송하지 않고 S3에 직접 올릴 URL을 발급합니다. (URL 5분, JPG · PNG · WEBP)

            1. 이 API에서 `photoKey`, `uploadUrl`, `headers`를 받습니다.
            2. `PUT uploadUrl` 요청의 body에 원본 파일 바이너리를 넣고, `headers`를 그대로 넣습니다.
            3. S3가 200을 반환하면 점검·청소·제보 API에 `beforeKey` 또는 `afterKey`로 `photoKey`를 보냅니다.

            파일의 사용자 소유권·형식·10MB 제한은 사진을 실제 활동에 연결할 때 서버가 S3 객체를 다시 확인합니다.
            """;

    private final StorageService storageService;

    /** 점검·청소·제보 사진을 올릴 S3 presigned PUT URL을 발급합니다. */
    @Operation(summary = "사진 업로드 URL 발급", description = PRESIGN_DESCRIPTION)
    @ApiErrorCodes({ErrorCode.INVALID_INPUT, ErrorCode.UNSUPPORTED_IMAGE_TYPE,
            ErrorCode.UNAUTHORIZED, ErrorCode.EXPIRED_TOKEN, ErrorCode.INVALID_TOKEN,
            ErrorCode.STORAGE_NOT_CONFIGURED})
    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PhotoPresignResponse>> presign(
            @LoginUserId Long userId, @Valid @RequestBody PhotoPresignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(PhotoPresignResponse.from(
                storageService.createUploadUrl(
                        userId, request.purpose().toUploadPurpose(), request.contentType()))));
    }
}
