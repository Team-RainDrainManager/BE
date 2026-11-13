-- 빗물받이별 날씨: 기상청 격자 단위 예보 · 현재 날씨 (Flyway V25)
-- 지금까지는 파일럿 격자 한 곳(역곡동 nx 57 · ny 125)만 썼다. 기존 데이터는 모두 그 격자로 옮긴다

-- 1) 빗물받이 격자 · 행정동 이름 (값은 애플리케이션이 계산 · 백필한다. 격자가 없으면 기본 격자로 본다)
ALTER TABLE "drain" ADD COLUMN "grid_nx" integer;
ALTER TABLE "drain" ADD COLUMN "grid_ny" integer;
ALTER TABLE "drain" ADD COLUMN "region_name" varchar(50);
CREATE INDEX idx_drain_grid ON "drain" ("grid_nx", "grid_ny") WHERE "deleted_at" IS NULL;

COMMENT ON COLUMN "drain"."grid_nx" IS '기상청 단기예보 격자 X (위경도로 계산, null이면 기본 격자)';
COMMENT ON COLUMN "drain"."grid_ny" IS '기상청 단기예보 격자 Y';
COMMENT ON COLUMN "drain"."region_name" IS '행정동 이름 (카카오 좌표→행정구역, 알림 문구용)';

-- 2) 예보는 격자마다 따로. 기존 예보는 파일럿 격자 예보다
--    DEFAULT는 기존 행 채우기와 SQL로 직접 넣는 테스트 데이터용. 애플리케이션은 항상 값을 넣는다
ALTER TABLE "rain_forecast" ADD COLUMN "grid_nx" integer NOT NULL DEFAULT 57;
ALTER TABLE "rain_forecast" ADD COLUMN "grid_ny" integer NOT NULL DEFAULT 125;

DROP INDEX uq_rain_forecast_active;
-- 같은 날짜 · 같은 격자의 진행 중 예보는 실제 · 가상 각각 1개
CREATE UNIQUE INDEX uq_rain_forecast_active
  ON "rain_forecast" ("rain_date", "grid_nx", "grid_ny", "is_simulated")
  WHERE "status" = 'ACTIVE' AND "deleted_at" IS NULL;

COMMENT ON COLUMN "rain_forecast"."grid_nx" IS '기상청 단기예보 격자 X';
COMMENT ON COLUMN "rain_forecast"."grid_ny" IS '기상청 단기예보 격자 Y';

-- 3) 현재 날씨: 1행 → 격자별 행. 기존 1행은 파일럿 격자 행이 된다
ALTER TABLE "current_weather" ADD COLUMN "grid_nx" integer;
ALTER TABLE "current_weather" ADD COLUMN "grid_ny" integer;
UPDATE "current_weather" SET "grid_nx" = 57, "grid_ny" = 125;
ALTER TABLE "current_weather" ALTER COLUMN "grid_nx" SET NOT NULL;
ALTER TABLE "current_weather" ALTER COLUMN "grid_ny" SET NOT NULL;
-- id 컬럼과 함께 기본 키 · CHECK (id = 1)도 지워진다
ALTER TABLE "current_weather" DROP COLUMN "id";
ALTER TABLE "current_weather" ADD PRIMARY KEY ("grid_nx", "grid_ny");

COMMENT ON TABLE "current_weather" IS '기상청 격자별 현재 날씨 (초단기실황, 격자당 1행)';
COMMENT ON COLUMN "current_weather"."grid_nx" IS '기상청 격자 X';
COMMENT ON COLUMN "current_weather"."grid_ny" IS '기상청 격자 Y';
COMMENT ON COLUMN "current_weather"."simulated_raining_until" IS '시연용 강제 "비 오는 중" 종료 시각 (격자별, null이면 미사용)';
