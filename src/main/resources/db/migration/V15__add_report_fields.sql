-- 제보 종류에 악취(ODOR) 추가 (type은 varchar라 값 제약 없음)
COMMENT ON COLUMN "report"."type" IS 'BLOCKAGE / NEW_DRAIN / LOCATION_FIX / REMOVAL / ODOR';

-- 제보 촬영 위치 컬럼 추가 (기존 행은 빗물받이 위치로 채운다)
ALTER TABLE "report" ADD COLUMN "latitude" DOUBLE PRECISION;
ALTER TABLE "report" ADD COLUMN "longitude" DOUBLE PRECISION;

UPDATE "report" r
SET "latitude" = d."latitude", "longitude" = d."longitude"
FROM "drain" d
WHERE r."drain_id" = d."id"
  AND (r."latitude" IS NULL OR r."longitude" IS NULL);

COMMENT ON COLUMN "report"."latitude" IS '제보 촬영 위치 위도';
COMMENT ON COLUMN "report"."longitude" IS '제보 촬영 위치 경도';

-- 내 제보 목록 · 관리자 상태별 목록 조회용
CREATE INDEX ix_report_user_created ON "report" ("user_id", "created_at" DESC);
CREATE INDEX ix_report_status_created ON "report" ("status", "created_at" DESC);
