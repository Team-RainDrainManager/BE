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
| | GET · PATCH | `/users/me/settings` | 19 | 앱 알림, 야간 수신(06시 발송) 동의, 겨울 28일 주기 ⚠️ |
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
| | GET | `/drains/{id}/growth` | 10 | 성장 도감: 현재 Lv, XP, 다음 레벨까지 ⚠️ |
| | POST | `/admin/drains` | - | 현장 조사 등록 (좌표, 사진, `isRisk`) |
| | POST | `/drains/{id}/adoptions` | 04 | `{ nickname, safetyAgreed }` → 입양. 3개 초과면 `ADOPTION_LIMIT_EXCEEDED` ⚠️ |
| | GET | `/adoptions/me` | 05 | 내 빗물받이 목록 (2/3), 다음 점검일, 35일 경고 |
| | GET | `/adoptions/{id}` | 06 | 입양 상세 + 돌봄 정보 |
| | POST | `/adoptions/{id}/release` | 05, 06 | `{ reason }` 입양 그만두기 |
| | POST | `/adoptions/{id}/management-logs` | 07, 08, 09 | 정기·비 예보 점검 기록. `{ type, requestId?, beforePhotoKey, beforeDirtLevel, cleaned, afterPhotoKey?, afterDirtLevel?, memo, latitude, longitude, safetyAgreed }` → AI 판정, 위치 불일치, 얻은 XP |
| | POST | `/cleanings` | 13 | 대신 점검 청소 시작 `{ drainId, requestId, beforePhotoKey, latitude, longitude }` → AI 더러움 단계 |
| | POST | `/cleanings/{id}/verifications` | 13 | 청소 후 사진 인증 `{ afterPhotoKey }` → PASS/FAIL (실패 시 재시도) |

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
| | POST | `/reports` | 14, 15 | `{ drainId, type, photoKey, description, newLatitude?, newLongitude? }` → 접수. 승인 후 +5 XP |
| | GET | `/reports/me` | 16 | 내 제보 내역 |
| | GET | `/users/me/activities` | 17 | 점검 기록 · 제보 기록 · 경험치 내역 |
| | GET | `/users/me/activities.csv` | 17 | 활동 기록 CSV |
| | GET | `/users/me/volunteer-certificate` | 16 | 활동 확인서 (실명 인증 필요) |
| | GET | `/leaderboard?type=WEEKLY\|TOTAL\|ZONE\|ORG` | 18 | 주간 · 누적 · 구역별 · 단체별 ⚠️ |

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
| | GET | `/admin/reviews` | 사진 검토 대기 (AI 저확신 · 위치 불일치) |
| | PATCH | `/admin/reviews/{type}/{id}` | `{ decision: APPROVE \| REJECT }` (type = cleaning / management-log) |
| | GET | `/admin/reports?status=PENDING` | 제보 승인 대기 |
| | PATCH | `/admin/reports/{id}` | 승인 · 반려 |
| | PATCH | `/admin/drains/{id}/clear-blockage` | 막힘 표시 해제 (청소 후 사진이 없을 때) |
| | GET | `/admin/export.csv` | 미점검 · 활동 CSV |
| | GET | `/admin/rewards.csv?period=` | 리워드 대상 명단 |
| | POST | `/admin/users/{id}/sanctions` | 허위 제보 제재 (7일 · 14일 · 영구) ⚠️ |
| | POST | `/admin/orgs/{id}/zones` | 단체 구역 배정 ⚠️ |
| | POST | `/admin/demo/reset` | 시연 데이터 초기화 |

## ⚠️ 피그마 ↔ ERD 불일치 (결정 필요)

1. **입양 조건**: 피그마는 이름 + 안전 수칙 체크만 하면 바로 입양된다. ERD는 입양 전 청소 인증(`adoption.cleaning_id`)이 필수다.
2. **성장 레벨 위치**: 피그마는 "집사가 바뀌어도 성장 기록은 이어져요", "빗물받이별 Lv.0~50, 50 XP마다 1단계"다. 레벨이 배수구에 붙는다. ERD는 `adoption.level/score`라서 입양자가 바뀌면 초기화된다.
3. **단체 기능**: 피그마에는 20번 단체 운영 화면, 단체 구역 배정, 단체별 순위가 있다. ERD에는 단체 테이블이 없다. 구역별 순위도 구역 테이블이 없다.
4. **예보 시간 규칙**: 피그마는 "요청 24시간 전 · 무응답 6시간 후 공개 · 마감 비 3시간 전"으로, 옛 기획안 기준이다. 확정 규칙은 전날 06시 알림 · 20시 공개다.
5. **더러움 단계**: 피그마는 "0단계"(청소 후 깨끗)를 쓴다. ERD는 1~3단계다.
6. **XP 규칙**: 피그마는 정기 점검 +5 XP(빗물받이별 하루 1회), 제보 승인 +5 XP다. 개인 참여 점수와 빗물받이 XP가 따로 있다.
7. **ERD에 없는 기능**: 점검 쉬기(90일 1회), 겨울 28일 주기 선택, 야간 수신 동의, 허위 제보 제재, 막힘 표시 해제 상태.
8. **도구함**: ERD에는 있지만 피그마에 화면이 없다.
