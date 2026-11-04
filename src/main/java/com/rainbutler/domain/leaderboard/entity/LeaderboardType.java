package com.rainbutler.domain.leaderboard.entity;

/**
 * 리더보드 집계 기간입니다.
 *
 * <p>구역(ZONE) · 단체(ORG) 순위는 아직 구역 · 단체 테이블이 없어 지원하지 않습니다.
 */
public enum LeaderboardType {
    /** 이번 주 (월요일 00:00 ~ 현재) */
    WEEKLY,
    /** 전체 누적 */
    TOTAL
}
