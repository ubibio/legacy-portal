package kr.hull.portal.approval;

import kr.hull.portal.common.AuditLogger;
import org.springframework.stereotype.Component;

/**
 * 결재 감사로그 - 어떤 동작을 어떤 형식으로 남길지.
 *
 * [리팩토링] 레거시: ApprovalService 가 시각 포맷 · 줄 형식을 직접 조립했다
 *   → 감사로그 형식 책임만 이 클래스로 분리 (기법: 클래스 추출 · 이유: 감사 · 보안 정책 변경은 결재 흐름과 다른 변경 이유(SRP). R10)
 * [보존] 줄 형식 "[yyyy-MM-dd HH:mm:ss] {동작} id={결재id} by={행위자}", 생성 로그만 끝에 " type={유형}"
 *   - ApprovalNotificationOutputTest 가 고정한다.
 * [리팩토링] 시각 포맷 · 줄 조립(private write)을 ConsoleAuditLogger.write(action, id, userId) 로 옮김 (중복 제거)
 *   → 이 클래스는 "결재의 어떤 동작을 어떤 이름으로 남길지"만 정한다 (APPROVAL CREATE · SUBMIT · APPROVE · REJECT · CANCEL)
 */
@Component
public class ApprovalAuditRecorder {

    private final AuditLogger auditLogger;

    public ApprovalAuditRecorder(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void recordCreated(Approval approval, Long actorId) {
        // [보존] 생성 로그만 끝에 " type={유형}" - 덧붙임 정보(detail)로 넘긴다
        auditLogger.write("APPROVAL CREATE", approval.getId(), actorId, "type=" + approval.getType());
    }

    public void recordSubmitted(Approval approval, Long actorId) {
        auditLogger.write("APPROVAL SUBMIT", approval.getId(), actorId);
    }

    public void recordApproved(Approval approval, Long actorId) {
        auditLogger.write("APPROVAL APPROVE", approval.getId(), actorId);
    }

    public void recordRejected(Approval approval, Long actorId) {
        auditLogger.write("APPROVAL REJECT", approval.getId(), actorId);
    }

    public void recordCanceled(Approval approval, Long actorId) {
        auditLogger.write("APPROVAL CANCEL", approval.getId(), actorId);
    }
}
