-- ───────── 점검 쉬기 ─────────
ALTER TABLE "users" ADD COLUMN "pause_until" timestamp;
ALTER TABLE "users" ADD COLUMN "last_pause_requested_at" timestamp;

COMMENT ON COLUMN "users"."pause_until" IS '점검 쉬기 종료 시각. 이 시각 전이면 쉬는 중';
COMMENT ON COLUMN "users"."last_pause_requested_at" IS '마지막 쉬기 신청 시각 (90일 쿨다운 판정)';
