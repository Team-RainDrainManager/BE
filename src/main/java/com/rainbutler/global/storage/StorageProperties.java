package com.rainbutler.global.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 사진 저장소(S3) 설정입니다. ({@code rain-butler.s3})
 *
 * <p>버킷은 비공개 1개를 쓰고, 환경은 key 접두어로 나눕니다. (로컬 {@code local}, 운영 {@code prod})
 *
 * @param bucket 버킷 이름 (비어 있으면 업로드 · 사진 검증이 STORAGE_NOT_CONFIGURED)
 * @param region 리전
 * @param keyPrefix key 접두어 (환경 구분)
 * @param accessKey 로컬 전용 액세스 키. 운영은 비워 두고 EC2 IAM Role을 쓴다
 * @param secretKey 로컬 전용 시크릿 키
 * @param uploadUrlTtl 업로드 presigned URL 유효 기간
 * @param readUrlTtl 조회 presigned URL 유효 기간
 * @param maxFileSize 사진 최대 크기
 */
@ConfigurationProperties(prefix = "rain-butler.s3")
public record StorageProperties(
        String bucket,
        String region,
        String keyPrefix,
        String accessKey,
        String secretKey,
        Duration uploadUrlTtl,
        Duration readUrlTtl,
        DataSize maxFileSize) {

    /**
     * 버킷이 설정됐는지 확인합니다. 환경변수가 없으면 "${...}" 문자열이 들어올 수 있어 함께 막습니다.
     *
     * @return 설정 여부
     */
    public boolean isConfigured() {
        return hasValue(bucket);
    }

    /**
     * 로컬용 정적 액세스 키가 둘 다 있는지 확인합니다. 없으면 기본 자격 증명(EC2 IAM Role)을 씁니다.
     *
     * @return 정적 키 사용 여부
     */
    public boolean hasStaticKeys() {
        return hasValue(accessKey) && hasValue(secretKey);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank() && !value.startsWith("${");
    }
}
