package kr.hull.portal.approval;

/**
 * 결재 처리(상신 · 승인 · 반려 · 취소) 명령 - 누가 · 어떤 처리를 · 어떤 사유로.
 *
 * [리팩토링 R12] 레거시: processApproval(id, userId, action, reason) - Long, Long 이 나란히 있어
 *   결재 id 와 사용자 id 를 바꿔 넣어도 컴파일된다 → 처리 대상(id)은 그대로 두고, 요청 내용 3개를 이 record 로 묶는다
 *   (기법: 매개변수 객체 도입). 요청 DTO(approval.dto.ProcessRequest)와 따로 둔 이유는 DraftApprovalCommand 와 같다.
 * [보존] action 은 API 계약상 정수(1 상신 · 2 승인 · 3 반려 · 9 취소) - 해석은 서비스가 ApprovalAction 으로 한다.
 */
public record ProcessApprovalCommand(Long userId, int action, String reason) {
}
