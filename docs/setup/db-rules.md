# DB 규칙

- 스키마는 **Flyway만** 바꾼다. JPA는 `ddl-auto: validate`로 엔티티와 테이블이 맞는지 검사만 한다.
- 마이그레이션 위치: `src/main/resources/db/migration`
- `V1__init.sql`은 `docs/erd/schema.sql`(ERD v6.3)을 그대로 옮긴 것이다. **머지 후 절대 수정하지 않는다.**
- 이후 변경은 항상 새 파일로 추가한다: `V2__설명.sql`, `V3__설명.sql` …
  - 예: `V2__add_users_rest_until.sql`
  - 이미 머지된 버전 파일을 고치면 Flyway 체크섬 검증이 실패한다.
- 스키마를 바꾸면 `docs/erd/`도 함께 갱신하고, 엔티티 매핑을 맞춘다.
- `validate` 불일치 에러가 나면 SQL이 아니라 **엔티티 매핑을 고친다.**
- 로컬 DB: `docker compose up -d` (PostgreSQL 16, DB · 계정 · 비밀번호 모두 `rainbutler`)
