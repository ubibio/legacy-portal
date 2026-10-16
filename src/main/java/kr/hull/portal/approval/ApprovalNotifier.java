package kr.hull.portal.approval;

import kr.hull.portal.common.MailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 결재 알림 메일 - 누구에게 · 어떤 말머리 · 어떤 문구로 보낼지.
 *
 * [리팩토링] 레거시: ApprovalService 가 메일 수신자 결정 · 본문 조립까지 직접 했다
 *   → 알림 책임만 이 클래스로 분리 (기법: 클래스 추출 · 이유: 메일 문구 · 알림 정책 변경은 결재 흐름과 다른 변경 이유(SRP). R10)
 * [보존] 수신자(상신 → 결재자, 승인 · 반려 → 기안자), 제목 "[말머리] 제목",
 *   본문 "안녕하세요 {이름}님,\n{안내문}\n제목: {제목}{추가 줄}", 수신자를 못 찾으면 생략,
 *   수신자 id 가 null 이면 findById 에서 예외(레거시 동작) - ApprovalNotificationOutputTest 가 고정한다.
 */
@Component
public class ApprovalNotifier {

    private final UserRepository userRepository;
    private final MailSender mailSender;

    public ApprovalNotifier(UserRepository userRepository, MailSender mailSender) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    /** 상신 → 결재자에게 [결재요청] (기안자ID 줄 포함). */
    public void notifySubmitted(Approval approval) {
        send(approval.getApproverId(), "결재요청", "결재 요청이 도착했습니다.", approval,
                "\n기안자ID: " + approval.getDrafterId());
    }

    /** 승인 → 기안자에게 [결재승인]. */
    public void notifyApproved(Approval approval) {
        send(approval.getDrafterId(), "결재승인", "결재가 승인되었습니다.", approval, "");
    }

    /** 반려 → 기안자에게 [결재반려] (사유 줄 포함). */
    public void notifyRejected(Approval approval, String reason) {
        send(approval.getDrafterId(), "결재반려", "결재가 반려되었습니다.", approval, "\n사유: " + reason);
    }

    private void send(Long recipientId, String subjectTag, String message, Approval approval, String extraLine) {
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        String body = "안녕하세요 " + recipient.getName() + "님,\n"
                + message + "\n제목: " + approval.getTitle()
                + extraLine;
        mailSender.send(recipient.getEmail(), "[" + subjectTag + "] " + approval.getTitle(), body);
    }
}
