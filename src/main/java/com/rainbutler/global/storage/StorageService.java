package com.rainbutler.global.storage;

import com.rainbutler.global.exception.BusinessException;
import com.rainbutler.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 사진 저장소(S3) 공통 서비스입니다.
 *
 * <p>프론트는 presigned PUT URL로 S3에 직접 올리고, 사진을 쓰는 API에는 S3 key를 보냅니다. DB 사진
 * 컬럼({@code *_photo_url})에는 key를 저장하고, 응답에서는 {@link #toReadUrl}로 조회 URL로 바꿉니다.
 *
 * <p>key 형식: {@code {prefix}/{purpose}/{userId}/{UUID}.{ext}}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final String HTTP_PREFIX = "http";
    private static final int NOT_FOUND = 404;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    /**
     * 업로드용 presigned URL 발급 결과입니다.
     *
     * @param key 업로드할 S3 key (사진을 쓰는 API에 보낼 값)
     * @param uploadUrl PUT 업로드 URL
     * @param expiresAt URL 만료 시각
     * @param headers 업로드할 때 그대로 넣어야 하는 헤더 (서명에 포함됨)
     */
    public record PresignedUpload(
            String key, String uploadUrl, LocalDateTime expiresAt, Map<String, String> headers) {
    }

    /**
     * 사진 업로드용 presigned PUT URL을 발급합니다.
     *
     * @param userId 업로드하는 사용자 id (key에 들어가 본인 사진인지 검사하는 데 쓴다)
     * @param purpose 사진 용도
     * @param contentType 사진 타입 (image/jpeg · image/png · image/webp)
     * @return key · 업로드 URL · 만료 시각 · 헤더
     */
    public PresignedUpload createUploadUrl(Long userId, UploadPurpose purpose, String contentType) {
        requireConfigured();
        ImageType imageType = ImageType.fromContentType(contentType)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE));

        String key = properties.keyPrefix() + "/" + purpose.pathSegment() + "/" + userId + "/"
                + UUID.randomUUID() + "." + imageType.extension;
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(imageType.contentType)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(properties.uploadUrlTtl())
                        .putObjectRequest(putObject)
                        .build());

        return new PresignedUpload(
                key,
                presigned.url().toString(),
                LocalDateTime.ofInstant(presigned.expiration(), ZoneId.systemDefault()),
                Map.of("Content-Type", imageType.contentType));
    }

    /**
     * 사진 key가 요청자가 올린 정상 사진인지 확인합니다. 도메인 서비스는 사진을 저장하기 전에 반드시 호출합니다.
     *
     * <ol>
     *   <li>key 형식 · 용도가 맞지 않음 → INVALID_PHOTO_KEY</li>
     *   <li>다른 사용자가 올린 사진 → PHOTO_FORBIDDEN</li>
     *   <li>S3에 없음 → PHOTO_NOT_UPLOADED</li>
     *   <li>허용하지 않는 타입 → UNSUPPORTED_IMAGE_TYPE</li>
     *   <li>최대 크기 초과 → PHOTO_TOO_LARGE (그 객체는 삭제)</li>
     *   <li>그 밖의 S3 오류 → STORAGE_ERROR</li>
     * </ol>
     *
     * @param userId 요청자 id
     * @param key 사진 key
     * @param purpose 기대하는 사진 용도
     * @return 검증된 key (그대로 저장하면 된다)
     */
    public String verifyUploaded(Long userId, String key, UploadPurpose purpose) {
        requireConfigured();
        Long ownerId = parseOwnerId(key, purpose);
        if (!ownerId.equals(userId)) {
            throw new BusinessException(ErrorCode.PHOTO_FORBIDDEN);
        }

        HeadObjectResponse head = headObject(key);
        if (ImageType.fromContentType(head.contentType()).isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        long size = head.contentLength() == null ? 0 : head.contentLength();
        if (size > properties.maxFileSize().toBytes()) {
            deleteQuietly(key);
            throw new BusinessException(ErrorCode.PHOTO_TOO_LARGE);
        }
        return key;
    }

    /**
     * 사진 key가 허용한 용도 중 하나로 올린 정상 사진인지 확인합니다. (판독처럼 여러 용도 사진을 받는 API용)
     *
     * <p>용도는 key의 두 번째 경로({@code {prefix}/{purpose}/...})로 정하고, 그 용도로
     * {@link #verifyUploaded(Long, String, UploadPurpose)} 검증을 그대로 합니다.
     *
     * @param userId 요청자 id
     * @param key 사진 key
     * @param allowedPurposes 허용하는 사진 용도
     * @return 검증된 key
     * @throws BusinessException 허용하지 않는 용도이거나 형식이 틀린 key (INVALID_PHOTO_KEY) 외 검증 오류
     */
    public String verifyUploaded(
            Long userId, String key, Collection<UploadPurpose> allowedPurposes) {
        requireConfigured();
        UploadPurpose purpose = allowedPurposes.stream()
                .filter(candidate -> key != null && key.startsWith(
                        properties.keyPrefix() + "/" + candidate.pathSegment() + "/"))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PHOTO_KEY));
        return verifyUploaded(userId, key, purpose);
    }

    /**
     * 저장된 사진 값을 응답용 URL로 바꿉니다.
     *
     * <ul>
     *   <li>null → null</li>
     *   <li>{@code http}로 시작 (기존 목 데이터, 카카오 프로필) → 그대로</li>
     *   <li>S3 key → 조회용 presigned GET URL (로컬 계산이라 목록에서 여러 번 불러도 된다)</li>
     * </ul>
     *
     * @param keyOrUrl DB에 저장된 사진 값
     * @return 응답에 내려줄 URL (S3 미설정이면 null)
     */
    public String toReadUrl(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) {
            return null;
        }
        if (keyOrUrl.startsWith(HTTP_PREFIX)) {
            return keyOrUrl;
        }
        if (!properties.isConfigured()) {
            // 조회 화면이 깨지지 않게 사진만 비워서 내려준다
            log.warn("S3 미설정으로 사진 조회 URL을 만들 수 없습니다. key={}", keyOrUrl);
            return null;
        }
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.readUrlTtl())
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(keyOrUrl)
                                .build())
                        .build())
                .url()
                .toString();
    }

    /**
     * 사진 바이트를 내려받습니다. (AI 판정, 사진 해시용)
     *
     * @param key 사진 key
     * @return 사진 바이트
     */
    public byte[] download(String key) {
        requireConfigured();
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.PHOTO_NOT_UPLOADED);
        } catch (SdkException e) {
            log.error("[S3 다운로드 오류] key={} {}", key, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            log.error("S3 버킷(S3_BUCKET)이 설정되지 않았습니다.");
            throw new BusinessException(ErrorCode.STORAGE_NOT_CONFIGURED);
        }
    }

    /**
     * key 형식을 검사하고 key에 들어 있는 업로드 사용자 id를 꺼냅니다.
     */
    private Long parseOwnerId(String key, UploadPurpose purpose) {
        if (key == null) {
            throw new BusinessException(ErrorCode.INVALID_PHOTO_KEY);
        }
        Pattern pattern = Pattern.compile("^" + Pattern.quote(properties.keyPrefix()) + "/"
                + purpose.pathSegment() + "/(\\d+)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}"
                + "-[0-9a-f]{12}\\.(" + ImageType.extensionPattern() + ")$");
        Matcher matcher = pattern.matcher(key);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_PHOTO_KEY);
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_PHOTO_KEY);
        }
    }

    private HeadObjectResponse headObject(String key) {
        try {
            return s3Client.headObject(builder -> builder.bucket(properties.bucket()).key(key));
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND) {
                throw new BusinessException(ErrorCode.PHOTO_NOT_UPLOADED);
            }
            log.error("[S3 HeadObject 오류] key={} {}", key, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        } catch (SdkException e) {
            log.error("[S3 HeadObject 오류] key={} {}", key, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    /**
     * 크기 초과 사진을 지웁니다. 삭제 실패는 검증 결과에 영향을 주지 않도록 로그만 남깁니다.
     */
    private void deleteQuietly(String key) {
        try {
            s3Client.deleteObject(builder -> builder.bucket(properties.bucket()).key(key));
        } catch (SdkException e) {
            log.warn("[S3 크기 초과 사진 삭제 실패] key={} {}", key, e.getMessage());
        }
    }

    /**
     * 허용하는 사진 타입입니다. 확장자는 타입으로 정합니다.
     */
    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static Optional<ImageType> fromContentType(String contentType) {
            if (contentType == null) {
                return Optional.empty();
            }
            // "image/jpeg; charset=..." 같은 파라미터는 무시한다
            String mimeType = contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(type -> type.contentType.equals(mimeType))
                    .findFirst();
        }

        static String extensionPattern() {
            return String.join("|", Arrays.stream(values()).map(type -> type.extension).toList());
        }
    }
}
