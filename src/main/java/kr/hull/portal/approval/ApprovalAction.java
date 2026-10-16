package kr.hull.portal.approval;

import java.util.Optional;

/**
 * 결재 처리 구분 (POST /api/approvals/{id}/process 의 action).
 *
 * [리팩토링] 레거시: processApproval 이 "action == 1 / 2 / 3 / 9" 숫자로 분기 → 여기서 enum 으로 (기법: 매직넘버를 enum 으로 · R10)
 *   - 이유: 처리 코드 체계(API 계약)가 바뀌는 것은 결재 흐름과 다른 변경 이유다(SRP). 숫자 해석을 한곳에 모은다.
 * [보존] API 로 받는 값은 레거시 정수 그대로(1 상신 · 2 승인 · 3 반려 · 9 취소).
 *   정의되지 않은 값(4~8 등)은 빈 Optional - 서비스가 조용히 무시한다(레거시 동작).
 */
public enum ApprovalAction {

    SUBMIT(1),    // 상신
    APPROVE(2),   // 승인
    REJECT(3),    // 반려
    CANCEL(9);    // 취소 (레거시 code 9 그대로)

    private final int code;

    ApprovalAction(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 레거시 정수 code → enum. 모르는 code 는 예외 대신 빈 값 (레거시: 어느 분기에도 안 걸려 무시). */
    public static Optional<ApprovalAction> fromCode(int code) {
        for (ApprovalAction action : values()) {
            if (action.code == code) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
