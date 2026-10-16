package kr.hull.portal.approval;

import kr.hull.portal.common.MailFormat;
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

    /*
     * [리팩토링] Extract Method - 알림마다 본문 조립을 이름 있는 private 메서드(submittedBody · approvedBody · rejectedBody)로 추출
     *   - 레거시: approve · reject 분기 안에 "안녕하세요 " + 이름 + ... 긴 문자열 조립이 그대로 박혀 있었다
     *   - 직전(R10): 인자 5개(수신자 id · 말머리 · 안내문 · 결재 · 추가 줄)를 받는 공통 send() 로 합쳤지만,
     *     호출부만 봐서는 실제 본문 모양이 안 보였다 → 본문마다 메서드 이름으로 의도를 드러낸다
     *   - 본문 메서드는 private 로 유지 (메일 문구는 이 클래스 밖에 노출하지 않는다)
     * [보존] 본문 · 제목 문자열 1글자도 동일, 수신자 결정 · 못 찾으면 생략 · 수신자 id null 이면 예외 (ApprovalNotificationOutputTest)
     * [리팩토링] 인사말 · 제목 줄 양식은 common.MailFormat 한 곳으로 (공지 긴급 메일과의 유사 중복 제거).
     *   이 클래스에는 "누구에게 · 어떤 말머리 · 어떤 안내문 · 끝에 붙일 줄"만 남는다
     */

    /** 상신 → 결재자에게 [결재요청]. */
    public void notifySubmitted(Approval approval) {
        User approver = findRecipient(approval.getApproverId());
        if (approver == null) {
            return;
        }
        mailSender.send(approver.getEmail(), MailFormat.subject("결재요청", approval.getTitle()), submittedBody(approver, approval));
    }

    /** 승인 → 기안자에게 [결재승인]. */
    public void notifyApproved(Approval approval) {
        User drafter = findRecipient(approval.getDrafterId());
        if (drafter == null) {
            return;
        }
        mailSender.send(drafter.getEmail(), MailFormat.subject("결재승인", approval.getTitle()), approvedBody(drafter, approval));
    }

    /** 반려 → 기안자에게 [결재반려]. */
    public void notifyRejected(Approval approval, String reason) {
        User drafter = findRecipient(approval.getDrafterId());
        if (drafter == null) {
            return;
        }
        mailSender.send(drafter.getEmail(), MailFormat.subject("결재반려", approval.getTitle()), rejectedBody(drafter, approval, reason));
    }

    // [보존] 수신자를 못 찾으면 null → 메일 생략. id 가 null 이면 findById 가 예외 (레거시 동작)
    private User findRecipient(Long recipientId) {
        return userRepository.findById(recipientId).orElse(null);
    }

    private String submittedBody(User approver, Approval approval) {
        return MailFormat.body(approver.getName(), "결재 요청이 도착했습니다.", approval.getTitle())
                + "\n기안자ID: " + approval.getDrafterId();
    }

    private String approvedBody(User drafter, Approval approval) {
        return MailFormat.body(drafter.getName(), "결재가 승인되었습니다.", approval.getTitle());
    }

    private String rejectedBody(User drafter, Approval approval, String reason) {
        return MailFormat.body(drafter.getName(), "결재가 반려되었습니다.", approval.getTitle())
                + "\n사유: " + reason;
    }
}
