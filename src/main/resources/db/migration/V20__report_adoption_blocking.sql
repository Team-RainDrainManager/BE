-- AI가 청소 불가로 판정한 막힘 제보가 빗물받이 입양을 막는다.
-- 기존 제보는 모두 false로 두어 지금까지의 동작(표시만)을 유지한다.
ALTER TABLE "report" ADD COLUMN "blocks_adoption" boolean NOT NULL DEFAULT FALSE;

-- 승인된 제보의 입양 차단 해제 시각 (관리자 해제 전에는 null)
ALTER TABLE "report" ADD COLUMN "cleared_at" timestamp;

COMMENT ON COLUMN "report"."blocks_adoption" IS 'AI 청소 불가 판정으로 입양을 막는 제보인지';
COMMENT ON COLUMN "report"."cleared_at" IS '승인된 제보의 입양 차단 해제 시각';
COMMENT ON COLUMN "report"."status" IS 'PENDING / APPROVED / REJECTED / CLEARED';

-- 빗물받이별 입양 차단 · 막힘 표시 조회용
CREATE INDEX ix_report_drain_blocking
    ON "report" ("drain_id", "blocks_adoption", "status");
