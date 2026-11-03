-- 빗물받이 표시 이름 · 주소 (지도 · 목록 · 상세에서 사용)
ALTER TABLE "drain" ADD COLUMN "name" varchar(50);
ALTER TABLE "drain" ADD COLUMN "address" varchar(200);

-- 기존 빗물받이는 이름이 없으므로 표시용 기본 이름을 채운다. 입양된 빗물받이는 별명이 우선한다.
UPDATE "drain" SET "name" = '빗물받이 #' || "id" WHERE "name" IS NULL;

COMMENT ON COLUMN "drain"."name" IS '사용자 지정 이름. 입양 전 등록 이름이며 입양 후에는 입양 별명이 우선한다.';
COMMENT ON COLUMN "drain"."address" IS '역지오코딩 또는 사용자 입력 주소. 없으면 null.';

-- 표시 상태(미입양 · 정상 · 점검 지연 · 막힘 제보) 계산에 쓰는 조회 조건
CREATE INDEX ix_drain_adoption_status ON "drain" ("adoption_status");
CREATE INDEX ix_adoption_drain_status ON "adoption" ("drain_id", "status");
CREATE INDEX ix_report_drain_type_status ON "report" ("drain_id", "type", "status");
