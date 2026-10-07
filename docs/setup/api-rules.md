# API 규칙

## 응답 형식
- 컨트롤러는 항상 `ResponseEntity<ApiResponse<T>>`를 반환한다.
- 성공: `ResponseEntity.ok(ApiResponse.ok(data))`, 데이터가 없으면 `ApiResponse.ok()`

```json
// 성공
{ "success": true, "data": { }, "error": null }

// 실패
{ "success": false, "data": null,
  "error": { "code": "ADOPTION_LIMIT_EXCEEDED", "message": "빗물받이는 최대 3개까지 입양할 수 있어요.",
             "fieldErrors": [ { "field": "nickname", "reason": "공백일 수 없습니다" } ] } }
```

- `fieldErrors`는 입력값 검증 실패 때만 채워지고, 그 외에는 빈 배열이다.

## 예외
- 실패는 응답을 직접 만들지 말고 예외를 던진다: `throw new BusinessException(ErrorCode.DRAIN_NOT_FOUND);`
- 새 에러는 `global/exception/ErrorCode`의 해당 도메인 구역에 추가한다. 메시지는 사용자에게 보이는 한국어 존댓말.
- `error.code`는 enum 이름이 그대로 나가며 프론트가 분기에 쓰므로, 한번 정한 이름은 바꾸지 않는다.
- 변환은 `GlobalExceptionHandler`가 한다. 5xx 응답에는 내부 원인을 넣지 않는다.

## 경로
- 모든 API는 `/api/v1` 아래에 둔다.
- 문서: `/swagger-ui`, 명세: `/v3/api-docs`
