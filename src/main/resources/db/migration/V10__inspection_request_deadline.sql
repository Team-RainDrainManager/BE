-- 비 예보 점검 요청 완료 마감 · 취소 · 놓침 시각 (Flyway V10)

ALTER TABLE "inspection_request" ADD COLUMN "complete_deadline_at" timestamp;
UPDATE "inspection_request" SET "complete_deadline_at" = "reply_deadline_at" WHERE "complete_deadline_at" IS NULL;
ALTER TABLE "inspection_request" ALTER COLUMN "complete_deadline_at" SET NOT NULL;
ALTER TABLE "inspection_request" ADD COLUMN "cancelled_at" timestamp;
ALTER TABLE "inspection_request" ADD COLUMN "missed_at" timestamp;

COMMENT ON COLUMN "inspection_request"."reply_deadline_at" IS '응답 마감 (기본 발송 당일 12:00, 당일 예보는 발송 + 1시간). 지나면 대신 점검 공개';
COMMENT ON COLUMN "inspection_request"."complete_deadline_at" IS '완료 마감 (기본 20:00, 당일 예보는 비 시작 1시간 전). 지나면 MISSED';
COMMENT ON COLUMN "inspection_request"."sent_at" IS '발송 시각 (기본 06:00, 당일 예보는 즉시)';
COMMENT ON COLUMN "inspection_request"."cancelled_at" IS '취소 시각 (예보 취소 · 비가 일찍 와서 취소)';
COMMENT ON COLUMN "inspection_request"."missed_at" IS '완료 마감까지 미완료로 시청 인계된 시각';

CREATE INDEX idx_inspection_request_status_deadline ON "inspection_request" ("status", "reply_deadline_at", "complete_deadline_at");
