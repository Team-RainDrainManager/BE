package com.rainbutler.global.response;

/**
 * 모든 API의 공통 응답 형식입니다.
 *
 * <pre>
 * 성공: { "success": true,  "data": { ... }, "error": null }
 * 실패: { "success": false, "data": null,
 *        "error": { "code": ..., "message": ..., "fieldErrors": [] } }
 * </pre>
 *
 * @param success 요청 성공 여부
 * @param data 성공 시 응답 데이터
 * @param error 실패 시 에러 정보
 * @param <T> 응답 데이터 타입
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    /**
     * 데이터를 담은 성공 응답을 만듭니다.
     *
     * @param data 응답 데이터
     * @return 성공 응답
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 데이터 없는 성공 응답을 만듭니다.
     *
     * @return 성공 응답
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 실패 응답을 만듭니다.
     *
     * @param error 에러 정보
     * @return 실패 응답
     */
    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
