package kr.hull.portal.approval;

import java.time.LocalDateTime;

/**
 * 결재 응답 (생성 · 내 기안함 · 받은 결재함).
 *
 * [리팩토링] 레거시: 컨트롤러가 Approval 엔티티를 그대로 JSON 으로 반환 → 응답 DTO(record)로 분리
 *   (기법: 응답 DTO 도입 · 이유: 엔티티 구조를 바꾸면 API 가 같이 바뀌고, 엔티티에 메서드를 더할 때마다 JSON 노출을 신경 써야 했다(스멜 S12). R11)
 * [보존] 필드 12개의 이름 · 순서 · 타입이 레거시 엔티티 JSON 과 같다. status · type · priority 는 정수 code.
 *   ApprovalControllerTest(MockMvc)가 이 형식을 고정한다.
 */
public record ApprovalResponse(
        Long id,
        String title,
        String content,
        int type,
        int status,
        int priority,
        Long drafterId,
        Long approverId,
        String rejectReason,
        long amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    static ApprovalResponse from(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTitle(),
                approval.getContent(),
                approval.getType(),
                approval.getStatus(),   // [보존] enum 이 아니라 정수 code
                approval.getPriority(),
                approval.getDrafterId(),
                approval.getApproverId(),
                approval.getRejectReason(),
                approval.getAmount(),
                approval.getCreatedAt(),
                approval.getUpdatedAt());
    }
}
