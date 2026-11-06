package com.rainbutler.domain.user.certificate;

/**
 * 발급이 끝난 활동 확인서입니다.
 *
 * @param content PDF 바이트
 * @param filename 다운로드 파일 이름
 */
public record IssuedCertificate(byte[] content, String filename) {
}
