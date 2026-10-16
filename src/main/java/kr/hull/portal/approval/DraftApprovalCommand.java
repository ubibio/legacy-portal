package kr.hull.portal.approval;

/**
 * 결재 기안(생성) 명령 - 서비스 · 도메인 계층이 쓰는 입력 묶음.
 *
 * [리팩토링 R12] 레거시: ApprovalService.create(title, content, type, priority, drafterId, approverId, amount, urgent)
 *   인자 8개 (스멜 S13 긴 매개변수 목록 · 데이터 뭉치 - int,int · Long,Long 이 나란히 있어 순서를 바꿔 넣어도 컴파일된다)
 *   → 함께 다니는 값을 이 record 하나로 묶는다 (기법: 매개변수 객체 도입 Introduce Parameter Object)
 * 왜 요청 DTO(approval.dto.CreateApprovalRequest)를 그대로 쓰지 않나:
 *   - 요청 DTO 는 웹 계층의 모양(JSON 필드, 생략 시 null 인 박싱 타입)에 맞춘 것이다. 서비스 · 엔티티가 그걸 받으면
 *     도메인이 HTTP 요청 형식에 묶인다 → 도메인 전용 명령을 따로 두고, 컨트롤러 쪽에서 DTO → 명령으로 바꿔 넘긴다.
 *   - 그래서 의존 방향은 dto → approval(이 명령) 한쪽뿐이다. 이 record 는 dto 패키지를 모른다.
 * [보존] 필드 타입은 레거시 create 시그니처 그대로(int · long · boolean 은 기본형, id 는 Long) - 값 의미 · 기본값 규칙 변화 없음.
 */
public record DraftApprovalCommand(
        String title,
        String content,
        int type,
        int priority,
        Long drafterId,
        Long approverId,
        long amount,
        boolean urgent) {
}
