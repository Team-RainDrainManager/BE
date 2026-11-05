-- 관리자 사진 검토(승인 · 반려)를 기록하고, 검토 대기 목록을 빠르게 조회합니다.

-- 점검 기록에는 검토자 · 검토 시각이 없어 추가합니다. (cleaning · management_log에는 이미 있음)
ALTER TABLE inspection ADD COLUMN reviewed_by BIGINT REFERENCES users(id);
ALTER TABLE inspection ADD COLUMN reviewed_at timestamp;

-- 관리 기록 승인 시 빗물받이 최신 사진과 함께 반영할 청소 후 더러움 단계입니다.
-- 지금까지는 응답에만 쓰고 저장하지 않아, 검토 승인 시 청소 전 단계로 덮어쓸 수밖에 없었습니다.
ALTER TABLE management_log ADD COLUMN after_dirt_level int;

-- 검토 대기(PENDING)만 모아 보므로 부분 인덱스를 씁니다.
CREATE INDEX ix_inspection_pending
    ON inspection (created_at DESC)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

CREATE INDEX ix_management_log_pending
    ON management_log (created_at DESC)
    WHERE review_status = 'PENDING' AND deleted_at IS NULL;

CREATE INDEX ix_cleaning_pending
    ON cleaning (created_at DESC)
    WHERE review_status = 'PENDING' AND deleted_at IS NULL;
