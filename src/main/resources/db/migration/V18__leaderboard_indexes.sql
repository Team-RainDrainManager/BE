-- 리더보드 집계용 인덱스.
-- 주간 순위는 기간(created_at)으로 거른 뒤 사용자별로 합산하고,
-- 누적 순위는 사용자별로 합산한다.
CREATE INDEX idx_point_history_created_at
    ON point_history (created_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_point_history_user_id
    ON point_history (user_id)
    WHERE deleted_at IS NULL;
