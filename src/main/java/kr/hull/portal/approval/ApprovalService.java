package kr.hull.portal.approval;

import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이었다.
 *
 * ============================ 레거시에 심어둔 스멜 목록 (해결 상태) ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.   → R09 · R10 해결
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.           → R08 해결
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다. → status · action 해결, type · priority · role 남음(R07)
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.            → R02 · R03 · R10 해결
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).    → R02 · R03 해결
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.      → R09 해결
 *  7. Primitive Obsession  : 모든 분기를 int 비교로 처리한다.                                  → status · action 해결
 *  8. Long Parameter List  : create() 파라미터 8개.                                          → R12 해결 (명령 객체 + 기존 시그니처는 위임)
 *  9. Poor Naming          : d, u, proc, tmp, flag1 같은 약어.                               → 이 클래스는 해결
 * 10. Comment Smell        : 나쁜 이름을 주석으로 변명한다.                                    → 이 클래스는 해결
 * 11. No Tests             : 테스트가 단 한 개도 없다(안전망 부재).                             → 특성화 테스트 58개
 * ==========================================================================================
 *
 * [리팩토링 R10] 책임 분리 (SRP - 변경 이유별로 나눔)
 *   레거시 이 클래스의 변경 이유 5가지          → 지금 맡는 곳
 *   - 결재 업무 흐름(조회 → 처리 → 저장 → 알림)   → 이 클래스 (유일하게 남긴 책임)
 *   - 처리 코드(action 1/2/3/9) 해석            → ApprovalAction enum
 *   - 결재 생성 규칙(긴급 → 우선순위 3 등)        → Approval.draft 정적 팩토리
 *   - 알림 메일 수신자 · 문구                   → ApprovalNotifier
 *   - 감사로그 형식                           → ApprovalAuditRecorder
 *   (상태 전이 · 권한 · 금액 등급은 R09 에서 Approval · User 엔티티로 이미 이동)
 * [보존] public 메서드 이름 · 시그니처 전부, 조용히 무시하는 동작, 저장 → 메일 → 감사로그 순서,
 *   메일 · 감사로그 문자열(ApprovalNotificationOutputTest), DB 정수값, 응답 JSON.
 *   검증: ApprovalServiceTest 6개 호출 · 기대값 수정 없이 green
 *
 * [리팩토링 R13 · REQ-NF-004] LocalDateTime.now() 직접 호출 4곳 → 주입받은 Clock 으로 LocalDateTime.now(clock)
 *   (기법: 숨은 의존성(시스템 시계) 주입 · 이유: 테스트에서 시각을 고정해 createdAt · updatedAt 을 정확히 비교)
 *   - 생성자: 기존 4인자 생성자는 그대로 두고(테스트가 직접 new 한다 · 시스템 기본 Clock), Clock 을 받는 5인자 생성자를 더한다.
 *     둘 다 @Autowired(required = false) - 스프링은 Clock 빈이 있으면(앱 · @SpringBootTest) 5인자, 없으면(@DataJpaTest 슬라이스) 4인자를 고른다.
 *   - [보존] 기록되는 시각의 의미(시스템 기본 시간대의 지금) · public 메서드 시그니처 그대로.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    // [리팩토링 R10] MailSender · AuditLogger 를 직접 쓰지 않고, 결재 전용 알림 · 감사 협력 객체를 주입받는다
    private final ApprovalNotifier approvalNotifier;
    private final ApprovalAuditRecorder approvalAuditRecorder;
    // [리팩토링 R13] 현재 시각의 출처 - LocalDateTime.now() 직접 호출 대신 (common.ClockConfig)
    private final Clock clock;

    /** [보존] 기존 생성자 - 시스템 기본 Clock 을 쓴다. Clock 빈이 없는 슬라이스 테스트 · 직접 new 하는 테스트용. */
    @Autowired(required = false)
    public ApprovalService(ApprovalRepository approvalRepository, UserRepository userRepository,
                           ApprovalNotifier approvalNotifier, ApprovalAuditRecorder approvalAuditRecorder) {
        this(approvalRepository, userRepository, approvalNotifier, approvalAuditRecorder, Clock.systemDefaultZone());
    }

    /** [리팩토링 R13] Clock 을 받는 생성자 - 앱은 ClockConfig 의 빈, 테스트는 Clock.fixed(...) 를 넣는다. */
    @Autowired(required = false)
    public ApprovalService(ApprovalRepository approvalRepository, UserRepository userRepository,
                           ApprovalNotifier approvalNotifier, ApprovalAuditRecorder approvalAuditRecorder, Clock clock) {
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.approvalNotifier = approvalNotifier;
        this.approvalAuditRecorder = approvalAuditRecorder;
        this.clock = clock;
    }

    /**
     * 결재 생성 (임시저장) - 명령 객체를 받는 새 진입점. 컨트롤러는 이걸 부른다.
     * [리팩토링 R12] 인자 8개 → DraftApprovalCommand 하나 (기법: 매개변수 객체 도입). 생성 규칙은 Approval.draft 가 맡는다.
     */
    public Approval create(DraftApprovalCommand command) {
        Approval approval = Approval.draft(command, LocalDateTime.now(clock));
        approvalRepository.save(approval);
        approvalAuditRecorder.recordCreated(approval, command.drafterId());
        return approval;
    }

    /**
     * 결재 생성 (임시저장) - 레거시 시그니처.
     * [보존] public 시그니처(파라미터 8개)는 레거시 계약이라 지우지 않는다 - 특성화 테스트가 이걸 부른다.
     * [리팩토링 R12] 본문은 값을 명령 객체로 묶어 새 create 로 위임만 한다 (로직이 한 곳에만 있게).
     */
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        return create(new DraftApprovalCommand(title, content, type, priority,
                drafterId, approverId, amount, urgent));
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소.
     * [리팩토링 R10] action 숫자 분기 → ApprovalAction enum switch
     * [보존] public 시그니처 processApproval(id, userId, action, reason) 그대로, action 은 API 계약상 정수.
     *   없는 결재 · 없는 사용자 · 정의되지 않은 action 은 예외 없이 조용히 무시 (레거시 동작).
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        // [리팩토링 R12] 레거시 시그니처는 명령 객체로 묶어 위임만 한다
        processApproval(id, new ProcessApprovalCommand(userId, action, reason));
    }

    /**
     * 결재 처리 - 명령 객체를 받는 새 진입점. 컨트롤러는 이걸 부른다.
     * [리팩토링 R12] (id, userId, action, reason) → (처리 대상 id, ProcessApprovalCommand) (기법: 매개변수 객체 도입)
     * [보존] 아래 동작 전부 레거시와 동일.
     */
    public void processApproval(Long id, ProcessApprovalCommand command) {
        Long userId = command.userId();
        Approval approval = approvalRepository.findById(id).orElse(null);
        if (approval == null) {
            return;   // [보존] 없는 결재 id - 조용히 리턴
        }
        User requester = userRepository.findById(userId).orElse(null);
        if (requester == null) {
            return;   // [보존] 없는 사용자 - 조용히 리턴
        }
        ApprovalAction approvalAction = ApprovalAction.fromCode(command.action()).orElse(null);
        if (approvalAction == null) {
            return;   // [보존] 정의되지 않은 action(4~8 등) - 조용히 리턴
        }
        switch (approvalAction) {
            case SUBMIT -> submit(approval, userId);
            case APPROVE -> approve(approval, requester);
            case REJECT -> reject(approval, requester, command.reason());
            case CANCEL -> cancel(approval, userId);
        }
    }

    // 각 처리: 엔티티에 시키고(조건 불만족이면 false → 조용히 끝) → 저장 → 알림 → 감사로그. [보존] 이 순서 그대로

    /** 상신. [보존] 요청자가 기안자인지는 보지 않는다 - 레거시 동작(재설계 단계 결함 후보). */
    private void submit(Approval approval, Long requesterId) {
        if (!approval.submit(LocalDateTime.now(clock))) {
            return;
        }
        approvalRepository.save(approval);
        approvalNotifier.notifySubmitted(approval);
        approvalAuditRecorder.recordSubmitted(approval, requesterId);
    }

    private void approve(Approval approval, User requester) {
        if (!approval.approve(requester, LocalDateTime.now(clock))) {
            return;
        }
        approvalRepository.save(approval);
        approvalNotifier.notifyApproved(approval);
        approvalAuditRecorder.recordApproved(approval, requester.getId());
    }

    private void reject(Approval approval, User requester, String reason) {
        if (!approval.reject(requester, reason, LocalDateTime.now(clock))) {
            return;
        }
        approvalRepository.save(approval);
        approvalNotifier.notifyRejected(approval, reason);
        approvalAuditRecorder.recordRejected(approval, requester.getId());
    }

    /** 취소. [보존] 메일 없이 감사로그만. */
    private void cancel(Approval approval, Long requesterId) {
        if (!approval.cancel(requesterId, LocalDateTime.now(clock))) {
            return;
        }
        approvalRepository.save(approval);
        approvalAuditRecorder.recordCanceled(approval, requesterId);
    }

    /** 상태 표시 라벨. [보존] public 시그니처, 상태가 없으면 "알수없음". 라벨은 ApprovalStatus 가 갖는다(R06 후속). */
    public String statusLabel(Approval approval) {
        ApprovalStatus status = approval.approvalStatus();
        return status == null ? "알수없음" : status.getLabel();
    }

    /**
     * 금액 등급.
     * [리팩토링] Move Method - 계산은 AmountGrade enum 이 한다 (Approval.amountGrade() → AmountGrade.of 위임).
     * [보존] public 시그니처 · 반환 타입 String 그대로, 값은 "S" · "A" · "B" · "C" (enum 이름).
     */
    public String amountGrade(Approval approval) {
        return approval.amountGrade().name();
    }

    public List<Approval> myDrafts(Long userId) {
        return approvalRepository.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return approvalRepository.findByApproverId(userId);
    }
}
