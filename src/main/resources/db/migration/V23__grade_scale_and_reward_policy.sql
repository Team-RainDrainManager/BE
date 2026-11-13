-- AI 등급을 화면 더러움 단계와 같은 1~3으로 맞춘다.
-- 예전에는 괜찮음이 0이었고, 1은 v6 이전 기준의 '조금 쌓임'이었다.
-- 새 기준에서는 1 = 괜찮음, 2 = 쌓임, 3 = 막힘이다.

-- 1) 옛 1(조금 쌓임)을 2(쌓임)로 옮긴다. 먼저 해야 새 1(괜찮음)과 섞이지 않는다.
UPDATE inspection SET before_grade = 2 WHERE before_grade = 1;
UPDATE inspection SET after_grade = 2 WHERE after_grade = 1;
UPDATE "cleaning" SET "after_grade" = 2 WHERE "after_grade" = 1;

-- 2) 괜찮음 0을 1로 옮긴다.
UPDATE inspection SET before_grade = 1 WHERE before_grade = 0;
UPDATE inspection SET after_grade = 1 WHERE after_grade = 0;
UPDATE "cleaning" SET "after_grade" = 1 WHERE "after_grade" = 0;
