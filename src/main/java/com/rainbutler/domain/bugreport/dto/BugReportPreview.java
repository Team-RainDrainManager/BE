package com.rainbutler.domain.bugreport.dto;

/**
 * 목록에 보여 줄 신고 내용 미리보기입니다.
 */
final class BugReportPreview {

    private static final int LENGTH = 100;

    private BugReportPreview() {
    }

    /**
     * 내용 앞 100자를 돌려줍니다. 잘렸으면 끝에 "…"를 붙입니다.
     *
     * @param content 내용
     * @return 미리보기
     */
    static String of(String content) {
        if (content == null || content.length() <= LENGTH) {
            return content;
        }
        return content.substring(0, LENGTH) + "…";
    }
}
