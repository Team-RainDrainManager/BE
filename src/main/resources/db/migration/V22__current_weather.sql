-- 역곡동 격자 현재 날씨 (기상청 초단기실황, Flyway V22)
-- 컨테이너가 둘 떠도 같은 값을 보게 DB에 1행만 둔다. 인프라 테이블이라 엔티티 · BaseEntity 공통 컬럼이 없다

CREATE TABLE "current_weather" (
  "id" smallint PRIMARY KEY DEFAULT 1 CHECK ("id" = 1),
  "pty" smallint,
  "rn1_mm" numeric(5,1),
  "raining" boolean NOT NULL DEFAULT false,
  "observed_at" timestamp,
  "fetched_at" timestamp NOT NULL,
  "simulated_raining_until" timestamp
);

COMMENT ON TABLE "current_weather" IS '역곡동 격자 현재 날씨 (초단기실황, 1행)';
COMMENT ON COLUMN "current_weather"."pty" IS '강수형태 (0 없음 · 1 비 · 2 비/눈 · 3 눈 · 5 빗방울 · 6 빗방울눈날림 · 7 눈날림)';
COMMENT ON COLUMN "current_weather"."rn1_mm" IS '1시간 강수량 (mm)';
COMMENT ON COLUMN "current_weather"."raining" IS '비 오는 중 (pty 1 · 2 · 5 · 6)';
COMMENT ON COLUMN "current_weather"."observed_at" IS '실황 기준 시각 (base_date + base_time)';
COMMENT ON COLUMN "current_weather"."fetched_at" IS '마지막으로 기상청 값을 반영한 시각';
COMMENT ON COLUMN "current_weather"."simulated_raining_until" IS '시연용 강제 "비 오는 중" 종료 시각 (null이면 미사용)';
