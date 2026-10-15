package kr.hull.portal.approval;

/**
 * 결재 상태.
 *
 * [리팩토링] 레거시 int status(0/1/2/3/9 매직넘버) → 여기서 의미 있는 enum 으로 (기법: 매직넘버를 enum 으로 · 원시 타입 집착 해소)
 *   - 이유: "s == 1" 만 보고는 상신인지 승인인지 알 수 없었고, 숫자 규칙이 서비스 곳곳에 흩어져 있었다.
 * [보존] DB 저장값 · API(JSON) 값은 레거시 정수 code 그대로다.
 *   - DB: ApprovalStatusConverter 가 code 로 저장한다. @Enumerated 는 쓰지 않는다
 *     (STRING 은 문자 저장, ORDINAL 은 순번 저장이라 CANCELED 가 9 가 아니라 4 로 어긋난다).
 *   - JSON: Approval.getStatus() 가 code(int)를 그대로 반환한다.
 */
public enum ApprovalStatus {

    DRAFT(0),       // 임시저장 - 만들었지만 아직 올리지 않음
    SUBMITTED(1),   // 상신 - 결재자에게 올림, 처리 대기
    APPROVED(2),    // 승인
    REJECTED(3),    // 반려
    CANCELED(9);    // 취소 (레거시 code 9 - 4~8 은 비어 있음, 값 그대로 보존)

    private final int code;

    ApprovalStatus(int code) {
        this.code = code;
    }

    /** DB · JSON 에 쓰는 레거시 정수 code. */
    public int getCode() {
        return code;
    }

    /**
     * 레거시 정수 code → enum.
     * 정의되지 않은 code 는 IllegalArgumentException - 레거시 코드에서 정의 밖 상태를 만드는 경로는 없다.
     */
    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("정의되지 않은 결재 상태 code: " + code);
    }
}
