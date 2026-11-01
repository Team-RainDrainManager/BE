-- 미관리 경고 발송 시각 (Flyway V11)

ALTER TABLE "adoption" ADD COLUMN "warned_at" timestamp;

COMMENT ON COLUMN "adoption"."warned_at" IS '같은 관리 주기에서 미관리 경고를 보낸 시각. last_managed_at이 더 나중이면 새 주기로 본다';
