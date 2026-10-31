package com.rainbutler.domain.drain.service;

import org.springframework.stereotype.Component;

/**
 * 빗물받이 성장 레벨 구간표입니다.
 *
 * <p>성장은 입양({@code adoption})에 귀속됩니다. 집사가 바뀌면 새 입양이 1레벨 · 0점으로 시작하므로
 * 성장 기록도 자동으로 초기화됩니다.
 *
 * <p>인덱스가 레벨, 값이 그 레벨이 되는 누적 점수입니다. 구간표가 바뀌면 {@link #MIN_SCORES}만
 * 교체하면 됩니다.
 */
@Component
public class GrowthLevelPolicy {

    /** 만렙 */
    public static final int MAX_LEVEL = 10;

    /**
     * 레벨별 최소 누적 점수입니다.
     *
     * <p>0번 인덱스는 사용하지 않습니다. 레벨 1은 0점에서 시작합니다.
     */
    private static final int[] MIN_SCORES = {
            0,    // 사용하지 않음
            0,    // Lv.1  씨앗
            50,   // Lv.2  새싹
            150,  // Lv.3  떡잎
            300,  // Lv.4  쏙쏙
            500,  // Lv.5  꽃봉오리
            800,  // Lv.6  꽃핀
            1200, // Lv.7  단골
            1700, // Lv.8  현장반장
            2400, // Lv.9  수호자
            3300  // Lv.10 전설 (만렙)
    };

    /**
     * 다음 레벨이 되는 누적 점수를 구합니다. 만렙이면 현재 점수를 그대로 돌려줍니다.
     *
     * @param level 현재 레벨
     * @param score 현재 점수 (만렙일 때 응답값으로 사용)
     * @return 다음 레벨 최소 누적 점수
     */
    public int nextLevelScore(int level, int score) {
        int current = normalizeLevel(level);
        if (current >= MAX_LEVEL) {
            return score;
        }
        return MIN_SCORES[current + 1];
    }

    /**
     * 현재 레벨 구간 안에서의 진행률을 구합니다. 값은 0 ~ 100으로 자릅니다.
     *
     * @param level 현재 레벨
     * @param score 현재 점수
     * @return 진행률 (%)
     */
    public int progressPercent(int level, int score) {
        int current = normalizeLevel(level);
        if (current >= MAX_LEVEL) {
            return 100;
        }
        int levelStartScore = MIN_SCORES[current];
        int nextLevelScore = MIN_SCORES[current + 1];
        int levelRange = nextLevelScore - levelStartScore;
        if (levelRange <= 0) {
            return 100;
        }
        int progressed = Math.max(0, score - levelStartScore);
        return Math.min(100, progressed * 100 / levelRange);
    }

    /** 저장된 레벨이 구간표 범위를 벗어나도 계산이 깨지지 않게 1 ~ 만렙으로 맞춥니다. */
    private int normalizeLevel(int level) {
        return Math.min(MAX_LEVEL, Math.max(1, level));
    }
}
