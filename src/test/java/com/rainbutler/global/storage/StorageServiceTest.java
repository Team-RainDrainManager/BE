package com.rainbutler.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * StorageService 단위 테스트입니다.
 *
 * <p>presign은 로컬 계산이라 실제 S3Presigner에 가짜 키를 넣어 씁니다. S3Client는 mock입니다. (AWS 호출 없음)
 */
class StorageServiceTest {

    private static final long USER_ID = 12L;
    private static final String KEY =
            "test/drain/12/3f2a9c1e-1234-4abc-9def-0123456789ab.jpg";

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret")))
                .build();
        storageService = new StorageService(s3Client, s3Presigner, properties("test-bucket"));
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
    }

    @Test
    void 업로드_URL을_발급하면_용도와_사용자가_들어간_key와_5분짜리_URL을_준다() {
        StorageService.PresignedUpload upload =
                storageService.createUploadUrl(USER_ID, UploadPurpose.BEFORE, "image/png");

        assertThat(upload.key()).matches("test/before/12/"
                + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
        assertThat(upload.uploadUrl())
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + upload.key())
                .contains("X-Amz-Expires=300");
        assertThat(upload.headers()).containsEntry("Content-Type", "image/png");
        assertThat(upload.expiresAt())
                .isBetween(LocalDateTime.now().plusMinutes(4), LocalDateTime.now().plusMinutes(6));
    }

    @Test
    void 타입별로_확장자가_정해진다() {
        assertThat(storageService.createUploadUrl(USER_ID, UploadPurpose.DRAIN, "image/jpeg").key())
                .endsWith(".jpg");
        assertThat(storageService.createUploadUrl(USER_ID, UploadPurpose.DRAIN, "image/webp").key())
                .endsWith(".webp");
    }

    @Test
    void 허용하지_않는_타입이면_UNSUPPORTED_IMAGE_TYPE() {
        assertErrorCode(() -> storageService.createUploadUrl(
                USER_ID, UploadPurpose.DRAIN, "text/plain"), ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        assertErrorCode(() -> storageService.createUploadUrl(
                USER_ID, UploadPurpose.DRAIN, "image/gif"), ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 버킷이_없으면_업로드와_검증_모두_STORAGE_NOT_CONFIGURED() {
        StorageService notConfigured =
                new StorageService(s3Client, s3Presigner, properties(""));

        assertErrorCode(() -> notConfigured.createUploadUrl(
                USER_ID, UploadPurpose.DRAIN, "image/jpeg"), ErrorCode.STORAGE_NOT_CONFIGURED);
        assertErrorCode(() -> notConfigured.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN),
                ErrorCode.STORAGE_NOT_CONFIGURED);
    }

    @Test
    void 정상_업로드된_사진이면_key를_돌려준다() {
        givenHead(HeadObjectResponse.builder().contentType("image/jpeg").contentLength(1024L)
                .build());

        assertThat(storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN)).isEqualTo(KEY);
    }

    @Test
    void key_형식이나_용도가_다르면_INVALID_PHOTO_KEY() {
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, "test/drain/12/abc.jpg",
                UploadPurpose.DRAIN), ErrorCode.INVALID_PHOTO_KEY);
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.AFTER),
                ErrorCode.INVALID_PHOTO_KEY);
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, "https://evil.com/a.jpg",
                UploadPurpose.DRAIN), ErrorCode.INVALID_PHOTO_KEY);
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, null, UploadPurpose.DRAIN),
                ErrorCode.INVALID_PHOTO_KEY);
    }

    @Test
    void 다른_사용자가_올린_사진이면_S3를_보지_않고_PHOTO_FORBIDDEN() {
        assertErrorCode(() -> storageService.verifyUploaded(99L, KEY, UploadPurpose.DRAIN),
                ErrorCode.PHOTO_FORBIDDEN);
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void S3에_없으면_PHOTO_NOT_UPLOADED() {
        given(s3Client.headObject(any(Consumer.class)))
                .willThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN),
                ErrorCode.PHOTO_NOT_UPLOADED);
    }

    @Test
    void 사진이_아닌_타입으로_올라갔으면_UNSUPPORTED_IMAGE_TYPE() {
        givenHead(HeadObjectResponse.builder().contentType("text/plain").contentLength(10L)
                .build());

        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN),
                ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 최대_크기를_넘으면_PHOTO_TOO_LARGE이고_그_사진을_지운다() {
        givenHead(HeadObjectResponse.builder().contentType("image/jpeg")
                .contentLength(DataSize.ofMegabytes(10).toBytes() + 1).build());

        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN),
                ErrorCode.PHOTO_TOO_LARGE);
        verify(s3Client).deleteObject(any(Consumer.class));
    }

    @Test
    void 그_밖의_S3_오류는_STORAGE_ERROR() {
        given(s3Client.headObject(any(Consumer.class)))
                .willThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN),
                ErrorCode.STORAGE_ERROR);

        given(s3Client.headObject(any(Consumer.class)))
                .willThrow(SdkClientException.create("connection reset"));
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, KEY, UploadPurpose.DRAIN),
                ErrorCode.STORAGE_ERROR);
    }

    @Test
    void 허용한_용도_중_하나로_올린_사진이면_통과한다() {
        givenHead(HeadObjectResponse.builder().contentType("image/jpeg").contentLength(1024L)
                .build());
        String beforeKey = "test/before/12/3f2a9c1e-1234-4abc-9def-0123456789ab.jpg";

        assertThat(storageService.verifyUploaded(USER_ID, beforeKey,
                Set.of(UploadPurpose.DRAIN, UploadPurpose.BEFORE, UploadPurpose.AFTER)))
                .isEqualTo(beforeKey);
    }

    @Test
    void 허용하지_않은_용도의_사진이면_S3를_보지_않고_INVALID_PHOTO_KEY() {
        String reportKey = "test/report/12/3f2a9c1e-1234-4abc-9def-0123456789ab.jpg";
        Set<UploadPurpose> allowed =
                Set.of(UploadPurpose.DRAIN, UploadPurpose.BEFORE, UploadPurpose.AFTER);

        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, reportKey, allowed),
                ErrorCode.INVALID_PHOTO_KEY);
        assertErrorCode(() -> storageService.verifyUploaded(USER_ID, (String) null, allowed),
                ErrorCode.INVALID_PHOTO_KEY);
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void 조회_URL은_null이면_null_http면_그대로_key면_1시간짜리_presigned_URL() {
        assertThat(storageService.toReadUrl(null)).isNull();
        assertThat(storageService.toReadUrl("https://k.kakaocdn.net/p.jpg"))
                .isEqualTo("https://k.kakaocdn.net/p.jpg");
        assertThat(storageService.toReadUrl(KEY))
                .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY)
                .contains("X-Amz-Expires=3600");
    }

    @Test
    void 버킷이_없으면_조회_URL은_null() {
        StorageService notConfigured = new StorageService(s3Client, s3Presigner, properties(""));

        assertThat(notConfigured.toReadUrl(KEY)).isNull();
    }

    @SuppressWarnings("unchecked")
    private void givenHead(HeadObjectResponse response) {
        given(s3Client.headObject(any(Consumer.class))).willReturn(response);
    }

    private static StorageProperties properties(String bucket) {
        return new StorageProperties(bucket, "ap-northeast-2", "test", "", "",
                Duration.ofMinutes(5), Duration.ofHours(1), DataSize.ofMegabytes(10));
    }

    private static void assertErrorCode(Executable call, ErrorCode expected) {
        assertThatThrownBy(call::execute)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
