-- 신규 등록(NEW_DRAIN) 제보는 대상 빗물받이가 없으므로 drain_id를 비울 수 있어야 한다.
ALTER TABLE "report" ALTER COLUMN "drain_id" DROP NOT NULL;

COMMENT ON COLUMN "report"."drain_id" IS '대상 빗물받이. 신규 등록(NEW_DRAIN) 제보는 null.';
