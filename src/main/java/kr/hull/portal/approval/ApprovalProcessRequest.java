package kr.hull.portal.approval;

/**
 * 결재 처리 요청 (POST /api/approvals/{id}/process 본문).
 *
 * [리팩토링] 레거시 Map<String, Object> → 요청 DTO(record) (기법: 요청 DTO 도입 · 스멜 S12 · 백로그 R11)
 * [보존] 필드 이름(userId · action · reason), action 정수 code(1 상신 · 2 승인 · 3 반려 · 9 취소) 그대로
 *   - reason 생략 → "" (레거시 body.getOrDefault("reason", ""))
 *   - userId · action 생략 → NullPointerException(500) (레거시 형변환과 같은 결과)
 * [알려진 차이 - 비정상 요청만] "reason": null 을 명시하면 레거시는 null 을 그대로 저장했지만, 지금은 "" 로 저장된다
 *   (record 는 "키 없음"과 "null" 을 구분하지 못한다).
 */
public record ApprovalProcessRequest(Long userId, Integer action, String reason) {

    /** [보존] 생략 시 빈 문자열. */
    String reasonOrEmpty() {
        return reason == null ? "" : reason;
    }
}
