package com.rainbutler.domain.terms.entity;

/**
 * 약관 종류입니다. 필수 · 선택 구분과 순서는 DB({@code terms})가 기준이고, 여기에는 종류만 둡니다.
 *
 * <p>⚠️ DB에 enum 이름이 문자열로 저장됩니다. 이름을 바꾸면 기존 데이터가 깨지므로 바꾸지 마세요.
 */
public enum TermsType {
    /** 만 14세 이상 확인 */
    AGE_14,
    /** 서비스 이용약관 */
    SERVICE,
    /** 개인정보 수집 · 이용 */
    PRIVACY,
    /** 위치정보 수집 · 이용 및 위치기반서비스 */
    LOCATION,
    /** 빗물받이 관리 안전 가이드 */
    SAFETY,
    /** 활동 알림 수신 */
    NOTIFICATION,
    /** 야간(22시~08시) 푸시 수신 */
    NIGHT_PUSH
}
