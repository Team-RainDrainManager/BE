package com.rainbutler.domain.photo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.rainbutler.domain.photo.dto.PhotoPresignRequest;
import com.rainbutler.domain.photo.dto.PhotoPurpose;
import com.rainbutler.global.response.ApiResponse;
import com.rainbutler.global.storage.StorageService;
import com.rainbutler.global.storage.UploadPurpose;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** C-1 사진 presign 응답 계약을 검증합니다. */
class PhotoControllerTest {

    @Test
    void BEFORE_사진에_photoKey와_S3_업로드_URL을_돌려준다() {
        StorageService storageService = mock(StorageService.class);
        PhotoController controller = new PhotoController(storageService);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 1, 12, 5);
        given(storageService.createUploadUrl(7L, UploadPurpose.BEFORE, "image/jpeg"))
                .willReturn(new StorageService.PresignedUpload(
                        "local/before/7/abc.jpg", "https://s3.example/upload", expiresAt,
                        Map.of("Content-Type", "image/jpeg")));

        ResponseEntity<ApiResponse<com.rainbutler.domain.photo.dto.PhotoPresignResponse>> result =
                controller.presign(7L, new PhotoPresignRequest(PhotoPurpose.BEFORE, "image/jpeg"));

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().photoKey()).isEqualTo("local/before/7/abc.jpg");
        assertThat(result.getBody().data().uploadUrl()).isEqualTo("https://s3.example/upload");
        assertThat(result.getBody().data().headers()).containsEntry("Content-Type", "image/jpeg");
    }
}
