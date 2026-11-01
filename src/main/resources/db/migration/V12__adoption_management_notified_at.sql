-- 관리 예정일 하루 전 알림 발송 시각 (Flyway V12)

ALTER TABLE "adoption" ADD COLUMN "management_notified_at" timestamp;

COMMENT ON COLUMN "adoption"."management_notified_at" IS '이번 관리 주기에서 관리 예정일 하루 전 알림을 보낸 시각. 관리 기록이 생기거나 예정일이 밀리면 비운다';
