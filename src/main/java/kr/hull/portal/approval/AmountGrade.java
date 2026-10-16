package kr.hull.portal.approval;

/**
 * 결재 금액 등급 (도메인 규칙).
 *
 * [리팩토링] Move Method - 레거시 ApprovalService.amountGrade 의 if 사다리(R09 에서 Approval.amountGrade 로 1차 이동)
 *   → 금액 등급 규칙 자체를 이 enum 으로 옮긴다 (기법: Move Method + 매직넘버를 enum 으로)
 *   - 이유: "등급 경계값과 등급 이름"은 결재 문서의 상태가 아니라 독립된 도메인 규칙이다.
 *     경계값(1000만 · 100만 · 10만)이 등급과 한곳에 있어야 등급을 추가 · 변경할 때 한 파일만 고친다.
 * [보존] 경계는 "이상"(>=) 그대로, 등급 문자 S · A · B · C 그대로 (ApprovalService.amountGrade 가 name() 을 돌려준다).
 *   ApprovalServiceSafetyNetTest 의 경계값 6개(10,000,000 / 9,999,999 / 1,000,000 / 999,999 / 100,000 / 99,999)가 고정한다.
 */
public enum AmountGrade {

    // 높은 등급부터 선언한다 - of() 가 위에서부터 첫 번째로 맞는 등급을 고른다
    S(10_000_000L),   // 1000만원 이상
    A(1_000_000L),    // 100만원 이상
    B(100_000L),      // 10만원 이상
    C(0L);            // 그 밖 (레거시 else 분기 - 음수 금액도 C)

    private final long minimumAmount;

    AmountGrade(long minimumAmount) {
        this.minimumAmount = minimumAmount;
    }

    public long getMinimumAmount() {
        return minimumAmount;
    }

    /** 금액 → 등급. [보존] 레거시 if (a >= 1000만) S else if (>= 100만) A else if (>= 10만) B else C 와 같은 결과. */
    public static AmountGrade of(long amount) {
        for (AmountGrade grade : values()) {
            if (grade != C && amount >= grade.minimumAmount) {
                return grade;
            }
        }
        return C;   // 레거시 else - 경계 검사 없이 나머지 전부 (음수 포함)
    }
}
