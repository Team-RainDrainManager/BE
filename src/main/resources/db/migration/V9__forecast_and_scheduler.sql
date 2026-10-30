-- 비 예보 수집 · 배치 스케줄러 (Flyway V9)

-- ShedLock: 배포 중 컨테이너가 둘 떠도 같은 배치가 한 번만 돌게 하는 잠금 테이블 (인프라 테이블, 엔티티 없음)
CREATE TABLE "shedlock" (
  "name" varchar(64) PRIMARY KEY,
  "lock_until" timestamp NOT NULL,
  "locked_at" timestamp NOT NULL,
  "locked_by" varchar(255) NOT NULL
);

COMMENT ON TABLE "shedlock" IS '배치 중복 실행 방지 잠금 (ShedLock이 직접 사용)';

-- 같은 날짜의 진행 중 예보는 실제 · 가상 각각 1개. 취소된 예보는 남기고 다시 확정되면 새 행을 만든다
CREATE UNIQUE INDEX uq_rain_forecast_active ON "rain_forecast" ("rain_date", "is_simulated")
  WHERE "status" = 'ACTIVE' AND "deleted_at" IS NULL;

ALTER TABLE "rain_forecast" ADD COLUMN "fetched_at" timestamp;

COMMENT ON COLUMN "rain_forecast"."fetched_at" IS '마지막으로 기상청 값을 반영한 시각';
COMMENT ON COLUMN "rain_forecast"."pop" IS '강수확률 최댓값 (%)';
COMMENT ON COLUMN "rain_forecast"."pcp_mm" IS '강수량 합계 (mm)';
