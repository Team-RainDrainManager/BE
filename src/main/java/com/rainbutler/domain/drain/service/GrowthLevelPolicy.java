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
    public static final int MAX_LEVEL = 50;

    /**
     * 레벨별 최소 누적 점수입니다.
     *
     * <p>0번 인덱스는 사용하지 않습니다. 레벨 1은 0점에서 시작합니다.
     */
    private static final int[] MIN_SCORES = {
            0,      // 사용하지 않음
            0,      // Lv.1
            50,     // Lv.2
            150,    // Lv.3
            300,    // Lv.4
            500,    // Lv.5
            800,    // Lv.6
            1200,   // Lv.7
            1700,   // Lv.8
            2400,   // Lv.9
            3300,   // Lv.10
            4400,   // Lv.11
            5700,   // Lv.12
            7200,   // Lv.13
            8900,   // Lv.14
            10800,  // Lv.15
            12900,  // Lv.16
            15200,  // Lv.17
            17700,  // Lv.18
            20400,  // Lv.19
            23300,  // Lv.20
            26400,  // Lv.21
            29700,  // Lv.22
            33200,  // Lv.23
            36900,  // Lv.24
            40800,  // Lv.25
            44900,  // Lv.26
            49200,  // Lv.27
            53700,  // Lv.28
            58400,  // Lv.29
            63300,  // Lv.30
            68400,  // Lv.31
            73700,  // Lv.32
            79200,  // Lv.33
            84900,  // Lv.34
            90800,  // Lv.35
            96900,  // Lv.36
            103200, // Lv.37
            109700, // Lv.38
            116400, // Lv.39
            123300, // Lv.40
            130400, // Lv.41
            137700, // Lv.42
            145200, // Lv.43
            152900, // Lv.44
            160800, // Lv.45
            168900, // Lv.46
            177200, // Lv.47
            185700, // Lv.48
            194400, // Lv.49
            203300  // Lv.50 만렙
    };

    /** 레벨별 칭호. 0번 인덱스는 미입양 표시에 쓴다. */
    private static final String[] TITLES = {
            "미입양",    // Lv.0 (입양 전)
            "씨앗",      // Lv.1
            "새싹",      // Lv.2
            "떡잎",      // Lv.3
            "쏙쏙",      // Lv.4
            "꽃봉오리",   // Lv.5
            "꽃핀",      // Lv.6
            "단골",      // Lv.7
            "현장반장",   // Lv.8
            "수호자",    // Lv.9
            "전설",      // Lv.10
            "신화",      // Lv.11
            "영웅",      // Lv.12
            "선구자",    // Lv.13
            "대장",      // Lv.14
            "지킴이",    // Lv.15
            "친구",      // Lv.16
            "동료",      // Lv.17
            "파트너",    // Lv.18
            "믿음직한",   // Lv.19
            "든든한",    // Lv.20
            "성실한",    // Lv.21
            "책임감 있는", // Lv.22
            "헌신적인",  // Lv.23
            "끈기 있는",  // Lv.24
            "용감한",    // Lv.25
            "활기찬",    // Lv.26
            "열정적인",  // Lv.27
            "성실한2",   // Lv.28
            "책임감 있는2", // Lv.29
            "헌신적인2", // Lv.30
            "끈기 있는2", // Lv.31
            "용감한2",   // Lv.32
            "활기찬2",   // Lv.33
            "열정적인2", // Lv.34
            "성실한3",   // Lv.35
            "책임감 있는3", // Lv.36
            "헌신적인3", // Lv.37
            "끈기 있는3", // Lv.38
            "용감한3",   // Lv.39
            "활기찬3",   // Lv.40
            "열정적인3", // Lv.41
            "성실한4",   // Lv.42
            "책임감 있는4", // Lv.43
            "헌신적인4", // Lv.44
            "끈기 있는4", // Lv.45
            "용감한4",   // Lv.46
            "활기찬4",   // Lv.47
            "열정적인4", // Lv.48
            "성실한5",   // Lv.49
            "책임감 있는5" // Lv.50
    };

    /**
     * 레벨에 해당하는 칭호를 구합니다. 입양 전(0 이하)은 미입양입니다.
     *
     * @param level 레벨
     * @return 칭호
     */
    public String titleFor(int level) {
        if (level <= 0) {
            return TITLES[0];
        }
        return TITLES[normalizeLevel(level)];
    }

    /**
     * 누적 점수에 해당하는 레벨을 구합니다.
     *
     * @param score 누적 점수
     * @return 레벨 (1 ~ 만렙)
     */
    public int levelFor(int score) {
        int level = 1;
        for (int candidate = 2; candidate <= MAX_LEVEL; candidate++) {
            if (score < MIN_SCORES[candidate]) {
                break;
            }
            level = candidate;
        }
        return level;
    }

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
