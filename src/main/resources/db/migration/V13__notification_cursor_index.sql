-- 알림 목록 커서 조회 인덱스 (Flyway V13)
-- 조회: WHERE user_id = ? AND id < :cursor ORDER BY id DESC LIMIT n

CREATE INDEX idx_notification_user_id_desc ON "notification" ("user_id", "id" DESC);
