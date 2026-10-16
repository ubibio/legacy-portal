package kr.hull.portal.approval;

import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.common.MailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
 *  7. Primitive Obsession  : 모든 분기를 int 비교로 처리한다.
 *  8. Long Parameter List  : create() 파라미터 8개.
 *  9. Poor Naming          : d, u, proc, tmp, flag1 같은 약어.
 * 10. Comment Smell        : 나쁜 이름을 주석으로 변명한다.
 * 11. No Tests             : 테스트가 단 한 개도 없다(안전망 부재).
 * =================================================================================
 *
 * [리팩토링] 레거시 processApproval(86줄 · 중첩 5단계) → submit/approve/reject/cancel 로 위임 (메서드 추출 · 조기 반환)
 *   - 해결: 스멜2(Long Method), 스멜9 일부(d·u·s·proc 이름 개선). 백로그 R08
 *   - 보존: public 시그니처, 상태 전이 조건 · 결과, 조용히 무시하는 동작, 메일 · 감사로그 문자열
 *   - 남은 것: 매직넘버(스멜3 → R06), 직접 new(스멜5 → R02·R03), 승인·반려 전제조건 중복(→ R09)
 *   - 검증: ApprovalServiceTest 특성화 테스트 6개 본문 수정 없이 green
 */
@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;   // [이름 개선] repo → approvalRepository (어떤 저장소인지 드러냄)
    private final UserRepository userRepository;   // [이름 개선] userRepo → userRepository (약어 제거)

    // [리팩토링] 레거시: new SmtpMailSender() · new FileAuditLogger() 직접 생성(스멜5 강결합)
    //   → 인터페이스(MailSender · AuditLogger) 타입 필드 + 생성자 주입 (기법: DIP · 이유: 구현 교체 · 테스트 대체 가능). 백로그 R02 · R03
    //   [이름 개선] mail → mailSender, audit → auditLogger
    private final MailSender mailSender;
    private final AuditLogger auditLogger;

    public ApprovalService(ApprovalRepository approvalRepository, UserRepository userRepository,
                           MailSender mailSender, AuditLogger auditLogger) {
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.auditLogger = auditLogger;
    }

    // [스멜8] 파라미터 8개. [스멜9] 이름이 모호하다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();   // [이름 개선] d → approval (레거시: 약어라 document? draft? 의미 불명)
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
        approval.changeStatus(ApprovalStatus.DRAFT);   // [enum 전환] setStatus(0) → DRAFT (DB 에는 0 그대로 저장)
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        // [리팩토링] 복붙된 감사로그 조립(스멜4) → writeAudit 공통 메서드 사용 (R02)
        //   [보존] 생성 로그만 끝에 " type=<유형>" 이 붙는다 - 꼬리(suffix) 인자로 그대로 재현
        writeAudit("APPROVAL CREATE", approval.getId(), drafterId, " type=" + approval.getType());
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     *
     * [리팩토링] 레거시 86줄 · 중첩 5단계 if-지옥 → action 별 private 메서드(submit/approve/reject/cancel)로 위임
     *   (기법: 메서드 추출 + 조기 반환(Guard Clause) · 이유: 한 메서드가 상태 전이 4종을 모두 떠안아 읽기·검증이 어려웠음)
     * [보존] public 시그니처 processApproval(id, userId, action, reason) 그대로. 정의되지 않은 action(4~8 등)은
     *   어느 분기에도 걸리지 않아 조용히 무시(레거시 동작 보존).
     * [개선 예정] action · status 숫자는 아직 매직넘버 → 백로그 R06 에서 enum(@Convert, DB 정수 유지)으로.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        // [이름 개선] d → approval, u → requester (레거시 약어를 그대로 옮기지 않음)
        Approval approval = approvalRepository.findById(id).orElse(null);
        if (approval == null) {
            return;   // [보존] 없는 결재 id - 예외 대신 조용히 리턴 (레거시 동작)
        }
        User requester = userRepository.findById(userId).orElse(null);
        if (requester == null) {
            return;   // [보존] 없는 사용자 - 조용히 리턴 (레거시 동작)
        }

        // [리팩토링] 레거시의 s(상태 복사) · proc(action 복사) 지역변수 제거 - 각 메서드가 approval 에서 직접 읽는다.
        //   상태는 분기 전 어디서도 바뀌지 않으므로 읽는 시점이 달라도 결과는 같다.
        if (action == 1) {            // 1 = 상신
            submit(approval, userId);
        } else if (action == 2) {     // 2 = 승인
            approve(approval, requester);
        } else if (action == 3) {     // 3 = 반려
            reject(approval, requester, reason);
        } else if (action == 9) {     // 9 = 취소
            cancel(approval, userId);
        }
    }

    /*
     * [리팩토링 R09] submit / approve / reject / cancel 의 전제조건 검사 · 상태 변경은 Approval 엔티티로 이동했다.
     *   서비스에는 "엔티티에 시키고 → 됐으면 저장 → 메일 · 감사로그" 의 조율만 남는다.
     *   [보존] 엔티티가 false(조건 불만족)를 돌려주면 저장 · 메일 · 로그 없이 조용히 끝난다 (레거시 동작).
     *          저장 → 메일 → 감사로그 순서, 메일 문구, 결재자 null 일 때 메일 조회에서 예외가 나는 동작도 그대로.
     */

    /** 상신 (레거시 action==1 분기). 요청자가 기안자인지는 보지 않는다 - 레거시 동작 보존. */
    private void submit(Approval approval, Long requesterId) {
        if (!approval.submit(LocalDateTime.now())) {   // [상태 전이 가드 → 엔티티] 임시저장이 아니면 false - 조용히 무시
            return;
        }
        approvalRepository.save(approval);

        // [보존] 결재자에게 [결재요청] 메일 - 결재자가 없으면 생략. 본문 문자열 1글자도 바꾸지 않음
        // [리팩토링] 복붙된 메일 본문 조립(스멜4) → sendApprovalMail 공통 메서드 (R03)
        sendApprovalMail(approval.getApproverId(), "결재요청", "결재 요청이 도착했습니다.", approval,
                "\n기안자ID: " + approval.getDrafterId());
        writeAudit("APPROVAL SUBMIT", approval.getId(), requesterId);
    }

    /**
     * 승인 (레거시 action==2 분기).
     * [리팩토링 R09] 상태 · 결재자 본인 · 권한 3단 검사(승인 · 반려에 복붙돼 있던 것) → Approval.approve 안의 canBeDecidedBy 하나로.
     *   [이름 개선] requesterId 파라미터 제거 - requester.getId() 와 같은 값이다(requester 는 그 id 로 조회한 사용자).
     */
    private void approve(Approval approval, User requester) {
        if (!approval.approve(requester, LocalDateTime.now())) {   // 상신 아님 · 결재자 아님 · 팀장 미만 → 조용히 무시
            return;
        }
        approvalRepository.save(approval);

        // [보존] 기안자에게 [결재승인] 메일 - 기안자가 없으면 생략 (추가 줄 없음)
        sendApprovalMail(approval.getDrafterId(), "결재승인", "결재가 승인되었습니다.", approval, "");
        writeAudit("APPROVAL APPROVE", approval.getId(), requester.getId());
    }

    /** 반려 (레거시 action==3 분기). 전제조건은 승인과 같고, 사유는 검증 없이 저장 - 레거시 동작 보존. */
    private void reject(Approval approval, User requester, String reason) {
        if (!approval.reject(requester, reason, LocalDateTime.now())) {   // 승인과 같은 조건 불만족 → 조용히 무시
            return;
        }
        approvalRepository.save(approval);

        // [보존] 기안자에게 [결재반려] 메일(사유 포함) - 기안자가 없으면 생략
        sendApprovalMail(approval.getDrafterId(), "결재반려", "결재가 반려되었습니다.", approval, "\n사유: " + reason);
        writeAudit("APPROVAL REJECT", approval.getId(), requester.getId());
    }

    /** 취소 (레거시 action==9 분기). 역할은 보지 않고 메일도 보내지 않는다 - 레거시 동작 보존. */
    private void cancel(Approval approval, Long requesterId) {
        if (!approval.cancel(requesterId, LocalDateTime.now())) {   // 승인 후 상태 · 기안자 아님 → 조용히 무시
            return;
        }
        approvalRepository.save(approval);
        writeAudit("APPROVAL CANCEL", approval.getId(), requesterId);
    }

    /**
     * [리팩토링] 상신 · 승인 · 반려에 복붙돼 있던 메일 본문 조립을 한 곳으로 (기법: 메서드 추출 · 중복 제거, 스멜4 · R03)
     * [보존] 본문 "안녕하세요 {이름}님,\n{안내문}\n제목: {제목}{추가 줄}", 제목 "[{말머리}] {제목}",
     *   수신자를 못 찾으면 메일 생략. 수신자 id 가 null 이면 findById 에서 예외가 나는 레거시 동작도 그대로.
     */
    private void sendApprovalMail(Long recipientId, String subjectTag, String message, Approval approval, String extraLine) {
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        String body = "안녕하세요 " + recipient.getName() + "님,\n"
                + message + "\n제목: " + approval.getTitle()
                + extraLine;
        mailSender.send(recipient.getEmail(), "[" + subjectTag + "] " + approval.getTitle(), body);
    }

    // [리팩토링] create() 에 남아 있던 복붙까지 이 메서드로 모음 (R02). [이름 개선] act → action, id → targetId, userId → actorId
    private void writeAudit(String action, Long targetId, Long actorId) {
        writeAudit(action, targetId, actorId, "");
    }

    // [보존] 줄 형식 "[yyyy-MM-dd HH:mm:ss] {동작} id={대상} by={행위자}{꼬리}" - 꼬리는 생성 로그의 " type=.." 용
    private void writeAudit(String action, Long targetId, Long actorId, String suffix) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        auditLogger.write("[" + now + "] " + action + " id=" + targetId + " by=" + actorId + suffix);
    }

    /**
     * 상태 표시 라벨.
     * [리팩토링] 레거시 if-else 사다리(숫자 → 라벨 번역표) → ApprovalStatus.getLabel() 위임 (기법: 데이터를 enum 으로 이동 · R06 후속)
     *   - 해결: 서비스가 화면 문자열 번역표를 갖던 것(스멜 S14 · SonarQube S6880 if-else 사슬)
     * [보존] public 시그니처 그대로, 라벨 문자열 그대로, 상태가 없으면(null) "알수없음" (레거시 기본값 분기)
     */
    public String statusLabel(Approval approval) {
        ApprovalStatus status = approval.approvalStatus();
        return status == null ? "알수없음" : status.getLabel();
    }

    /**
     * 금액 등급.
     * [리팩토링 R09] 레거시: 서비스가 approval.getAmount() 를 꺼내 등급을 계산(스멜6 기능 욕심 · 약어 a · 매직넘버)
     *   → Approval.amountGrade() 위임. 경계값은 엔티티의 이름 있는 상수로.
     * [보존] public 시그니처 그대로, 등급 경계(1000만 · 100만 · 10만 "이상") 그대로.
     */
    public String amountGrade(Approval approval) {
        return approval.amountGrade();
    }

    public List<Approval> myDrafts(Long userId) {
        return approvalRepository.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return approvalRepository.findByApproverId(userId);
    }
}
