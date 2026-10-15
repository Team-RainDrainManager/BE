package com.rainbutler.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트 설정입니다.
 *
 * <p>로컬은 {@code .env}의 정적 키를 씁니다. {@code .env}는 Spring 설정에만 들어가 OS 환경변수가 아니므로
 * SDK 기본 자격 증명이 읽지 못해 직접 넣습니다. 운영은 키 없이 EC2 IAM Role(기본 자격 증명)을 씁니다.
 *
 * <p>빈을 만들 때는 AWS를 호출하지 않으므로 키 · 버킷이 없어도 앱은 뜹니다.
 */
@Slf4j
@Configuration
public class S3Config {

    /**
     * S3 API 호출(HeadObject · GetObject · DeleteObject)용 클라이언트입니다.
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(StorageProperties properties) {
        if (!properties.isConfigured()) {
            log.warn("S3 버킷(S3_BUCKET)이 설정되지 않아 사진 업로드를 쓸 수 없습니다.");
        }
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    /**
     * presigned URL 서명용 객체입니다. 서명은 로컬 계산이라 네트워크를 쓰지 않습니다.
     */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    private static AwsCredentialsProvider credentialsProvider(StorageProperties properties) {
        if (properties.hasStaticKeys()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
