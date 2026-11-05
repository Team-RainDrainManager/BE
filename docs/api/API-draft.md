# 빗물받이 집사 API 초안 (피그마 21화면 × ERD v6.3)

> 화면 번호는 피그마 화면 전개도 번호. ★ = 1차 제출(10/30) 시연 흐름에 필요.
> ⚠️ = 피그마와 ERD가 달라서 팀 결정이 필요한 API.

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| Base URL | `/api/v1` |
| 인증 | `Authorization: Bearer {accessToken}` (카카오 로그인 후 서버 JWT 발급) |
| 권한 | `/admin/**` = CITY_OFFICIAL·ADMIN, `/admin/demo/**`·`/admin/drains` 등록 = ADMIN |
| 응답 형식 | `{ "success": true, "data": {...}, "error": null }` / 실패 시 `error: { code, message }` |
| 에러 코드 | `ADOPTION_LIMIT_EXCEEDED`, `REQUEST_ALREADY_CLAIMED`, `OUT_OF_RANGE`, `DUPLICATE_PHOTO` 처럼 도메인별 문자열 코드 |
| 페이지네이션 | 목록은 `?page=0&size=20` → `{ content, page, size, totalElements }` |
| 시각 | ISO-8601, 한국 시간 (`2026-10-06T17:11:00`) |
| 좌표 | `latitude`, `longitude` (double) |
| 사진 | 먼저 업로드 URL을 받아 S3에 올리고, 다른 API에는 `photoKey`만 보낸다 |

## 1. 인증 · 사용자 (파트 A)

| | Method | Path | 화면 | 설명 |
| --- | --- | --- | --- | --- |
| ★ | POST | `/auth/kakao` | 01 | 카카오 인가코드 → access/refresh 토큰. 신규면 가입 |
| | POST | `/auth/refresh` | - | 토큰 재발급 |
| | POST | `/auth/logout` | 16 | 로그아웃 |
| ★ | POST | `/auth/demo` | 01 | 시연 계정 로그인 (카카오 없이). 운영에서는 끔 |
| | GET | `/users/me` | 16 | 닉네임, 참여 점수, 입양 수, 점검 수, 봉사 시간 |
| | PATCH | `/users/me` | 16 | 닉네임 수정 |
| ★ | PUT | `/users/me/location` | 13 | 활동 위치 갱신 (대신 점검 300m 판정용) |
| | GET · PATCH | `/users/me/settings` | 19 | 앱 알림, 야간 수신(06시 발송) 동의. 관리 주기는 14일, 겨울(12~2월)은 서버가 28일 자동 적용 |
| | POST | `/users/me/rest` | 05, 19 | 점검 쉬기 신청 (90일에 한 번) ⚠️ |
| | POST | `/users/me/identity` | 16 | 실명 인증 (봉사 확인서용) |

## 2. 사진 · 알림 (파트 A)

| | Method | Path | 화면 | 설명 |
| --- | --- | --- | --- | --- |
| ★ | POST | `/photos/upload-url` | 07, 08, 14 | `{ purpose, sha256 }` → `{ uploadUrl, photoKey }`. 해시 중복이면 `DUPLICATE_PHOTO` |
| ★ | GET | `/notifications` | 11 | 알림함 목록 + 미확인 수 |
| | PATCH | `/notifications/{id}/read` | 11 | 읽음 |
| | PATCH | `/notifications/read-all` | 11 | 모두 읽음 |
| | GET | `/push/vapid-key` | 19 | 웹 푸시 공개키 |
| | POST · DELETE | `/push/subscriptions` | 19 | 웹 푸시 구독 등록·해제 |

## 3. 빗물받이 · 입양 · 점검 (파트 B)

| | Method | Path | 화면 | 설명 |
| --- | --- | --- | --- | --- |
| ★ | GET | `/drains?swLat&swLng&neLat&neLng&status=` | 02 | 지도 영역 안 빗물받이 + 상태 색(미입양·정상·점검 지연·막힘 제보) + 레벨 |
| | GET | `/drains/list?status=&page=` | 02 | "50개 목록" 탭 |
| ★ | GET | `/drains/{id}` | 03, 06 | 상세: 상태, 사진, 최근 점검, 성장(Lv·XP), 입양 여부, 내가 입양했는지 |
| | GET | `/drains/{id}/growth` | 10 | 성장 도감: Lv(1~10) · 점수 · 다음 레벨 점수 · 진행률. 입양 귀속(집사 변경 시 초기화) |
| | POST | `/admin/drains` | - | 현장 조사 등록 (좌표, 사진, `isRisk`) |
| | POST | `/drains/{id}/adoptions` | 04 | `{ nickname, safetyAgreed }` → 입양. 3개 초과면 `ADOPTION_LIMIT_EXCEEDED` ⚠️ |
| | GET | `/adoptions/me` | 05 | 내 빗물받이 목록 (2/3), 다음 점검일, 35일 경고 |
| ★ | GET | `/adoptions/{id}` | 06 | 입양 상세 + 돌봄 정보. 입양자 · 관리자만 조회 (해제된 입양은 404) |
| | POST | `/adoptions/{id}/release` | 05, 06 | `{ reason: MOVED \| NO_TIME \| OTHER }` 입양 그만두기(파양). 본인만 · 기록·성장 보관 · SENT·ACCEPTED 요청은 소유자 비우고 OPEN 재공개 |
| | POST | `/adoptions/{id}/management-logs` | 07, 08, 09 | 정기·비 예보 점검 기록(기존 API). `{ type, requestId?, actionType, beforePhotoKey, afterPhotoKey?, memo, latitude, longitude, safetyAgreed }` → AI 판정, 위치 불일치·낮은 확신도면 검토 대기. 인증 성공 시 관리 주기·빗물받이 최신 사진 갱신 (포인트·성장 지급은 별도) |
| ★ | GET | `/drains/{id}/inspection-eligibility?requestId=` | 07, 08, 09 | 점검 화면 진입 전 가능 여부. 정기·비 예보·대신 점검의 권한, 요청 상태, 마감, 제재 여부를 판정 |
| ★ | POST | `/inspections` | 07, 08, 09 | 점검·청소 기록 공통 제출(점검 전 사진). `{ drainId, kind, requestId?, beforeKey, latitude, longitude, capturedAt, note?, safetyConfirmed }` → AI 판정, 깨끗하면 바로 `COMPLETED`, 더러우면 `NEEDS_CLEANING`, 위치 불일치·낮은 확신도면 `PENDING`. 사진 재사용·요청 중복 제출 차단 |
| | POST | `/inspections/{id}/complete` | 08, 09 | 청소 후 사진 인증. `{ afterKey, safetyConfirmed }` → 청소 후 등급이 좋아지면 `COMPLETED`(관리 주기·빗물받이 사진·XP 갱신), 아니면 재청소 `NEEDS_CLEANING`, 위치·확신도 문제면 `PENDING` |
| ★ | GET | `/inspections/{id}` | 07, 08, 09 | 점검 기록 상세. `{ inspectionId, drain: { drainId, name }, kind, createdAt, review, reviewReason?, beforePhotoUrl?, afterPhotoUrl?, beforeGrade, afterGrade?, cleaned, distanceM, xp, note?, photoDeleted }`. 점검한 본인·해당 입양의 입양자·관리자만 조회. 촬영일부터 365일이 지나면 `photoDeleted=true`이고 사진 URL은 null |
| ★ | GET | `/adoptions/{id}/inspections?cursor=&size=` | 03, 06 | 입양별 점검 기록을 최신순 커서 페이지로 조회. `size` 기본 20·최대 50, 응답 `{ nextCursor, inspections: [...] }`. 입양자·관리자만 조회 |
| | POST | `/cleanings` | 13 | 대신 점검 청소 시작 `{ drainId, requestId, beforePhotoKey, latitude, longitude }` → AI 더러움 단계 |
| | POST | `/cleanings/{id}/verifications` | 13 | 청소 후 사진 인증 `{ afterPhotoKey }` → PASS/FAIL (실패 시 재시도) |

### 입양 상세 규칙

- `GET /adoptions/{id}`는 내 입양 목록(`/adoptions/me`)의 돌봄 정보에 빗물받이 정보 · 입양일을 더한다.
- 입양자 본인 · `ADMIN` · `CITY_OFFICIAL`만 볼 수 있다. 다른 사용자는 403 `FORBIDDEN`이다.
- 해제(`RELEASED`)된 입양은 404 `ADOPTION_NOT_FOUND`다. (목록과 같은 기준)
- 응답

```json
{
  "adoptionId": 12,
  "nickname": "또랑이",
  "adoptedAt": "2026-10-20T10:00:00",
  "level": 2,
  "score": 60,
  "levelTitle": "새싹",
  "nextLevelScore": 150,
  "progressPercent": 10,
  "lastManagedAt": "2026-11-02T09:00:00",
  "nextManagementAt": "2026-11-16T09:00:00",
  "daysUntilNextManagement": 12,
  "releaseWarning": false,
  "missedForecastCount": 0,
  "lonely": false,
  "displayStatus": "NORMAL",
  "displayStatusLabel": "정상",
  "drain": {
    "drainId": 8,
    "name": "역곡역 앞",
    "address": "경기도 부천시 역곡로",
    "latitude": 37.5,
    "longitude": 127.0,
    "photoUrl": "https://.../latest.jpg",
    "dirtLevel": 2,
    "risk": false
  }
}
```

- `daysUntilNextManagement`는 다음 관리 예정일까지 남은 일수다. 지났으면 음수, 예정일이 없으면 `null`이다.
- `displayStatus`는 `AVAILABLE` · `NORMAL` · `MANAGEMENT_DELAYED` · `BLOCKAGE_REPORTED` 중 하나다.

## 4. 비 예보 · 대신 점검 (파트 C)

| | Method | Path | 화면 | 설명 |
| --- | --- | --- | --- | --- |
| ★ | GET | `/requests/me` | 11, 12 | 내가 받은 점검 요청 (응답 대기 / 가능 / 마감까지 남은 시간) |
| ★ | POST | `/requests/{id}/reply` | 11 | `{ reply: ACCEPT \| DECLINE }`. 불가면 바로 대신 점검 공개 |
| ★ | GET | `/requests/open?latitude&longitude` | 13 | 반경 300m 안 공개된 대신 점검 목록 (거리, 남은 시간) |
| ★ | POST | `/requests/{id}/claim` | 13 | 선착순 1명. 실패 사유: 이미 수락됨·입양자 본인·반경 밖·하루 3건 초과 |
| ★ | POST | `/admin/forecasts/simulate` | 11~13, 21 | 가상 예보 생성 → 점검 요청 생성 + 알림 |
| ★ | POST | `/admin/demo/advance-time` | 21 | "시간 경과 시연": 06시 발송 / 20시 공개 / 마감 처리를 즉시 실행 |

## 5. 제보 · 활동 · 순위 (파트 C)

| | Method | Path | 화면 | 설명 |
| --- | --- | --- | --- | --- |
| | POST | `/reports` | 14, 15 | `{ drainId, type, photoKey?, description?, latitude?, longitude?, newLatitude?, newLongitude? }` → 접수(`PENDING`). 승인 후 +5 XP |
| | GET | `/reports/me` | 16 | 내 제보 내역 (최신순) |
| | GET | `/reports/{id}` | C-5 | 제보 상세. 제보자 본인 · `ADMIN` · `CITY_OFFICIAL`만 조회 |
| | GET | `/users/me/activities?type=&cursor=&size=` | 17 | 점검 기록 · 제보 기록 · 경험치 내역 (커서 페이지) |
| | GET | `/users/me/activities.csv` | 17 | 활동 기록 CSV |
| | GET | `/users/me/volunteer-certificate` | 16 | 활동 확인서 (실명 인증 필요) |
| | GET | `/leaderboard?type=WEEKLY\|TOTAL&limit=` | 18 | 주간 · 누적 참여 점수 순위 (구역 · 단체는 준비 중) |
| | GET | `/leaderboard/me?type=WEEKLY\|TOTAL` | 18 | 내 리더보드 순위 |

### 제보 규칙

- 제보 종류: `BLOCKAGE`(막힘) · `NEW_DRAIN`(신규) · `LOCATION_FIX`(위치 수정) · `REMOVAL`(철거) · `ODOR`(악취)
- 종류별 필수 값
  - `BLOCKAGE` · `NEW_DRAIN`: `photoKey`
  - `REMOVAL`: `photoKey` + `description`(철거 사유)
  - `LOCATION_FIX`: `photoKey` + `newLatitude` · `newLongitude`(새 위치, 둘 다)
  - `ODOR`: `photoKey` 또는 `description` 중 하나
- `latitude` · `longitude`는 제보 촬영 위치다. 보내지 않으면 빗물받이 위치로 채운다. 둘 중 하나만 보내면 400.
- `photoKey`는 `StorageService.verifyUploaded(..., REPORT)`로 검증한다. (`PHOTO_FORBIDDEN` · `PHOTO_NOT_UPLOADED` 등)
- 상태: `PENDING` → `APPROVED` | `REJECTED`. 이미 처리된 제보를 다시 처리하면 409 `REPORT_ALREADY_PROCESSED`.
- 승인 시 `processedBy` · `resolvedAt` · `adminComment`를 남기고 제보자에게 `report-xp`(기본 5)를 지급한다.
  반려는 `adminComment`(사유)가 필수이고 경험치를 지급하지 않는다.

### 활동 내역 규칙

- `type`: `ALL`(기본) · `INSPECTION` · `REPORT` · `POINT`. 생략하거나 `ALL`이면 세 종류를 합쳐 보여 준다.
- 정렬: `occurredAt` 내림차순. 같은 시각이면 점검 → 제보 → 경험치 순, 그다음 `id` 내림차순.
- 응답: `{ nextCursor, activities: [{ type, id, occurredAt, title, description, amount, drainId, drainName, status }] }`
- `nextCursor`는 다음 페이지가 있을 때만 내려간다. 클라이언트는 값을 해석하지 말고 그대로 `cursor`에 보낸다.
- `amount`: 점검 경험치(`xpAwarded`)와 경험치 내역의 증감. 적립은 양수, 회수는 음수다.
- `status`: 점검 · 제보의 상태 이름(`COMPLETED` 등). 경험치 행은 `null`.
- `size`: 기본 20, 최대 50(넘으면 50). 잘못된 `type` · `cursor` · `size`는 400 `INVALID_INPUT`.

### 리더보드 규칙

- `type`: `WEEKLY`(기본) · `TOTAL`. `WEEKLY`는 이번 주 월요일 00:00부터 현재까지, `TOTAL`은 전체 누적이다.
- `limit`: 상위 순위 개수. 기본 20, 최대 100(넘으면 100). 1보다 작으면 400 `INVALID_INPUT`.
- 점수: `point_history`의 적립(EARN) 합 − 회수(REVOKE) 합. 점수 0 이하와 관리자 · 시청 관계자는 순위에서 뺀다.
- 동점: 같은 순위를 주고 다음 순위를 건너뛴다. (예: 1, 1, 3) 동점 안에서는 `userId` 오름차순.
- 응답: `{ type, periodStart, totalParticipants, rankings, me }`
  - `rankings`: `{ rank, userId, nickname, profileImage, score, isMe }`
  - `me`: 내가 상위 `limit` 밖이어도 항상 내려간다. 점수가 0 이하이면 `rank`는 null이고, `score`는 실제 값(음수 포함)을 내려준다.
- `ZONE` · `ORG` 순위는 구역 · 단체 테이블이 정해지기 전까지 지원하지 않는다. (400 `INVALID_INPUT`)

## 6. 도구함 (파트 C) ⚠️ 피그마에 화면 없음

| | Method | Path | 설명 |
| --- | --- | --- | --- |
| | GET | `/tool-boxes` | 도구함 위치 · 남은 수량 |
| | POST | `/tool-boxes/{id}/rentals` | 대여 (1인 1개) |
| | POST | `/rentals/{id}/return` | 반납 (빌린 도구함에만) |

## 7. 시군 관리 (파트 C, 데스크탑 21번)

| | Method | Path | 설명 |
| --- | --- | --- | --- |
| | GET | `/admin/stats` | 입양률 · 정기 점검률 · 예보 점검 완료율 · 대신 점검 성사율 |
| ★ | GET | `/admin/requests?forecastId=` | 비 예보 점검 현황, 위험 미점검 목록 |
| ★ | GET | `/admin/reviews?type=&limit=` | 사진 검토 대기 (AI 저확신 · 위치 불일치). 화면 없음 |
| ★ | PATCH | `/admin/reviews/{type}/{id}` | `{ decision: APPROVE \| REJECT, reason? }` (type = `INSPECTION` · `MANAGEMENT_LOG` · `CLEANING`) |
| | GET | `/admin/reports?status=PENDING` | 제보 승인 대기 (status 생략 시 전체, 최신순). `ADMIN` · `CITY_OFFICIAL` |
| | PATCH | `/admin/reports/{id}` | `{ approved: true\|false, adminComment? }` 승인 · 반려. 반려 시 사유 필수. `ADMIN` · `CITY_OFFICIAL` |
| | PATCH | `/admin/drains/{id}/clear-blockage` | 막힘 표시 해제 (청소 후 사진이 없을 때) |
| | GET | `/admin/export.csv` | 미점검 · 활동 CSV |
| | GET | `/admin/rewards.csv?period=` | 리워드 대상 명단 |
| | POST | `/admin/users/{id}/sanctions` | 허위 제보 제재 (7일 · 14일 · 영구) ⚠️ |
| | POST | `/admin/orgs/{id}/zones` | 단체 구역 배정 ⚠️ |
| | POST | `/admin/demo/reset` | 시연 데이터 초기화 |

### 사진 검토 규칙

- 검토 대기(`PENDING`)는 촬영 위치가 기준 거리(기본 50m) 이상 떨어지거나 AI 확신도가 기준(0.7)보다 낮을 때 생긴다.
- `type`
  - `INSPECTION`: 점검 · 청소 기록 (`inspection.status = PENDING`)
  - `MANAGEMENT_LOG`: 입양 후 관리 기록 (`management_log.review_status = PENDING`)
  - `CLEANING`: 입양 전 · 대신 점검 청소 (`cleaning.review_status = PENDING`, 인증 통과한 청소만)
  - 하이픈 표기도 허용한다. (`management-log`)
- `GET /admin/reviews` 응답은 `{ reviews: [...] }`이고, 종류와 상관없이 같은 모양이다.
  - `{ type, id, createdAt, userId, nickname, drainId, drainName, beforePhotoUrl, afterPhotoUrl, beforeGrade, afterGrade, dirtLevel, aiConfidence, distanceM, locationMismatch, reason }`
  - 그 종류에 없는 값은 `null`이다.
  - `limit` 기본 20 · 최대 100, `type` 생략 시 세 종류를 모아 최신순으로 내려준다.
- `PATCH /admin/reviews/{type}/{id}`
  - 점검 승인: 인증을 통과한 점검과 같은 후처리를 한다. (관리 주기 · 빗물받이 최신 사진 · 경험치 · 연결된 점검 요청 완료)
  - 관리 기록 승인: AI 판정이 실패였더라도 인정해 관리 주기 · 빗물받이 최신 사진을 반영한다.
  - 반려: 점검 수 · 봉사 시간 집계에서 빠진다. 반려 사유는 필수(최대 500자)다.
  - 이미 처리된 검토는 409 `REVIEW_ALREADY_PROCESSED`, 대상이 없으면 404 `REVIEW_NOT_FOUND`다.
- 검토는 `ADMIN` · `CITY_OFFICIAL`만 할 수 있다.

## ⚠️ 피그마 ↔ ERD 불일치 (결정 필요)

1. **입양 조건**: 피그마는 이름 + 안전 수칙 체크만 하면 바로 입양된다. ERD는 입양 전 청소 인증(`adoption.cleaning_id`)이 필수다.
2. **성장 레벨 위치**: ~~피그마는 "집사가 바뀌어도 성장 기록은 이어져요", "빗물받이별 Lv.0~50, 50 XP마다 1단계"다.~~ **확정: ERD대로 입양(`adoption.level/score`)에 귀속하고, 집사가 바뀌면 초기화한다. 레벨은 Lv.1~10, 구간표는 0 · 50 · 150 · 300 · 500 · 800 · 1,200 · 1,700 · 2,400 · 3,300P.**
3. **단체 기능**: 피그마에는 20번 단체 운영 화면, 단체 구역 배정, 단체별 순위가 있다. ERD에는 단체 테이블이 없다. 구역별 순위도 구역 테이블이 없다.
4. **예보 시간 규칙**: 피그마는 "요청 24시간 전 · 무응답 6시간 후 공개 · 마감 비 3시간 전"으로, 옛 기획안 기준이다. 확정 규칙은 전날 06시 알림 · 20시 공개다.
5. **더러움 단계**: 피그마는 "0단계"(청소 후 깨끗)를 쓴다. ERD는 1~3단계다.
6. **XP 규칙**: 피그마는 정기 점검 +5 XP(빗물받이별 하루 1회), 제보 승인 +5 XP다. 개인 참여 점수와 빗물받이 XP가 따로 있다.
7. **ERD에 없는 기능**: 점검 쉬기(90일 1회), 야간 수신 동의, 허위 제보 제재, 막힘 표시 해제 상태. 겨울(12~2월) 28일 주기는 서버가 자동 적용한다.
8. **도구함**: ERD에는 있지만 피그마에 화면이 없다.
