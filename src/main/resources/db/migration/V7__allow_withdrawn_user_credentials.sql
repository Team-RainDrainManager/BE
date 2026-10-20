-- 회원 탈퇴: 탈퇴한 계정은 이메일 · 비밀번호 · 카카오 회원번호를 비우므로 로그인 자격 제약에서 제외한다
ALTER TABLE "users" DROP CONSTRAINT ck_users_provider_credentials;
ALTER TABLE "users" ADD CONSTRAINT ck_users_provider_credentials CHECK (
  "deleted_at" IS NOT NULL
  OR ("provider" = 'LOCAL' AND "email" IS NOT NULL AND "password" IS NOT NULL)
  OR ("provider" = 'KAKAO' AND "provider_id" IS NOT NULL));

COMMENT ON COLUMN "adoption"."release_reason" IS 'MOVED / NO_TIME / OTHER / AUTO(35일 미관리) / WITHDRAWN(회원 탈퇴)';
