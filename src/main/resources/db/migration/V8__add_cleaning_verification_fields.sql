-- 청소 완료 인증을 위한 필드 추가
ALTER TABLE "cleaning" ADD COLUMN "after_photo_url" VARCHAR(255);
ALTER TABLE "cleaning" ADD COLUMN "after_grade" INTEGER;
ALTER TABLE "cleaning" ADD COLUMN "attempt_no" INTEGER NOT NULL DEFAULT 0;
