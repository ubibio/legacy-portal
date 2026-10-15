package kr.hull.portal.approval;

import kr.hull.portal.common.FileAuditLogger;
import kr.hull.portal.common.SmtpMailSender;
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

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다.
    private final SmtpMailSender mail = new SmtpMailSender();
    private final FileAuditLogger audit = new FileAuditLogger();

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    // [스멜8] 파라미터 8개. [스멜9] 이름이 모호하다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();   // [이름 개선] d → approval (레거시: 약어라 document? draft? 의미 불명)
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
        approval.setStatus(0);                          // status(상태): 0 임시저장·1 상신·2 승인·3 반려·9 취소  [스멜3: 0=임시저장, DRAFT 대신 숫자 0]
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + now + "] APPROVAL CREATE id=" + approval.getId()
                + " by=" + drafterId + " type=" + approval.getType();
        audit.write(line);
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
        Approval approval = repo.findById(id).orElse(null);
        if (approval == null) {
            return;   // [보존] 없는 결재 id - 예외 대신 조용히 리턴 (레거시 동작)
        }
        User requester = userRepo.findById(userId).orElse(null);
        if (requester == null) {
            return;   // [보존] 없는 사용자 - 조용히 리턴 (레거시 동작)
        }

        // [리팩토링] 레거시의 s(상태 복사) · proc(action 복사) 지역변수 제거 - 각 메서드가 approval 에서 직접 읽는다.
        //   상태는 분기 전 어디서도 바뀌지 않으므로 읽는 시점이 달라도 결과는 같다.
        if (action == 1) {            // 1 = 상신
            submit(approval, userId);
        } else if (action == 2) {     // 2 = 승인
            approve(approval, requester, userId);
        } else if (action == 3) {     // 3 = 반려
            reject(approval, requester, userId, reason);
        } else if (action == 9) {     // 9 = 취소
            cancel(approval, userId);
        }
    }

    /**
     * [리팩토링] 레거시 processApproval 의 action==1 분기를 추출 (메서드 추출 + 조기 반환).
     * 상신: 임시저장(0)일 때만. 요청자가 기안자인지는 검사하지 않는다(레거시 동작 보존 - 재설계 단계 결함 후보).
     */
    private void submit(Approval approval, Long requesterId) {
        if (approval.getStatus() != 0) {   // [상태 전이 가드] 임시저장(0)일 때만 상신 - 아니면 조용히 무시(레거시 동작 보존)
            return;
        }
        // [보존] 지출(type 1) · 100만원 이상이면 상신 시 우선순위를 3(높음)으로 상향
        if (approval.getType() == 1 && approval.getAmount() >= 1000000) {
            approval.setPriority(3);
        }
        approval.setStatus(1);   // 1 = 상신 (SUBMITTED)
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [보존] 결재자에게 [결재요청] 메일 - 결재자가 없으면 생략. 본문 문자열 1글자도 바꾸지 않음
        User approver = userRepo.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            String body = "안녕하세요 " + approver.getName() + "님,\n"
                    + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                    + "\n기안자ID: " + approval.getDrafterId();
            mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL SUBMIT", approval.getId(), requesterId);
    }

    /**
     * [리팩토링] 레거시 processApproval 의 action==2 분기를 추출 (메서드 추출 + 조기 반환으로 중첩 3단계 → 0).
     * 승인: 상신(1) 상태 + 요청자가 지정된 결재자 + 팀장 이상(role >= 2) 일 때만.
     */
    private void approve(Approval approval, User requester, Long requesterId) {
        if (approval.getStatus() != 1) {   // [상태 전이 가드] 상신(1)일 때만 - 아니면 조용히 무시(재승인도 여기서 무시)
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(requesterId)) {
            return;   // [권한 가드] 지정된 결재자 본인이 아니면 조용히 무시(레거시 동작 보존)
        }
        if (requester.getRole() < 2) {   // role 1=사원·2=팀장·3=임원 → 팀장 미만이면 조용히 무시
            return;
        }
        approval.setStatus(2);   // 2 = 승인 (APPROVED)
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [보존] 기안자에게 [결재승인] 메일 - 기안자가 없으면 생략
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                    + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL APPROVE", approval.getId(), requesterId);
    }

    /**
     * [리팩토링] 레거시 processApproval 의 action==3 분기를 추출 (메서드 추출 + 조기 반환).
     * 반려: 승인과 같은 전제조건. 반려 사유는 검증 없이 그대로 저장(빈 문자열도 허용 - 레거시 동작 보존).
     * [개선 예정] 승인과 전제조건 3줄이 중복 → 백로그 R09 에서 Approval · User 의 판정 메서드로 이동.
     */
    private void reject(Approval approval, User requester, Long requesterId, String reason) {
        if (approval.getStatus() != 1) {   // [상태 전이 가드] 상신(1)일 때만 반려 - 아니면 조용히 무시
            return;
        }
        if (approval.getApproverId() == null || !approval.getApproverId().equals(requesterId)) {
            return;   // [권한 가드] 지정된 결재자 본인이 아니면 조용히 무시
        }
        if (requester.getRole() < 2) {   // 팀장(2) 미만이면 조용히 무시
            return;
        }
        approval.setStatus(3);   // 3 = 반려 (REJECTED)
        approval.setRejectReason(reason);
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        // [보존] 기안자에게 [결재반려] 메일(사유 포함) - 기안자가 없으면 생략
        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                    + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                    + "\n사유: " + reason;
            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), body);
        }
        writeAudit("APPROVAL REJECT", approval.getId(), requesterId);
    }

    /**
     * [리팩토링] 레거시 processApproval 의 action==9 분기를 추출 (메서드 추출 + 조기 반환).
     * 취소: 임시저장(0) 또는 상신(1) + 요청자가 기안자 본인일 때만. 역할(role)은 보지 않고 메일도 보내지 않는다(레거시 동작 보존).
     */
    private void cancel(Approval approval, Long requesterId) {
        int status = approval.getStatus();   // [이름 개선] s → status
        if (status != 0 && status != 1) {    // [상태 전이 가드] 승인 전(0·1)일 때만 취소 - 아니면 조용히 무시
            return;
        }
        if (approval.getDrafterId() == null || !approval.getDrafterId().equals(requesterId)) {
            return;   // [권한 가드] 기안자 본인이 아니면 조용히 무시
        }
        approval.setStatus(9);   // 9 = 취소 (CANCELED)
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);
        writeAudit("APPROVAL CANCEL", approval.getId(), requesterId);
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거).
    private void writeAudit(String act, Long id, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        audit.write("[" + now + "] " + act + " id=" + id + " by=" + userId);
    }

    // [스멜1][스멜10] 화면 표시용 문자열까지 서비스가 만든다. 주석으로 매직넘버를 변명한다.
    public String statusLabel(Approval approval) {   // [이름 개선] 파라미터 d → approval (호출부 영향 없음)
        int status = approval.getStatus();   // [이름 개선] s → status. 값: 0 임시저장·1 상신·2 승인·3 반려·9 취소
        String tmp;          // tmp = 결과 라벨(반환할 상태 표시 문자열)  [스멜9: 임시? 의도가 안 드러남]
        if (status == 0) tmp = "임시저장";       // status 0~9 를 라벨로 번역 — 이 표가 곳곳에 중복
        else if (status == 1) tmp = "상신";
        else if (status == 2) tmp = "승인";
        else if (status == 3) tmp = "반려";
        else if (status == 9) tmp = "취소";
        else tmp = "알수없음";              // enum 이면 컴파일러가 막아줄 분기
        return tmp;
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산.
    public String amountGrade(Approval approval) {   // [이름 개선] 파라미터 d → approval (호출부 영향 없음)
        long a = approval.getAmount();   // a = amount(금액, 원)  [스멜9: 한 글자 약어]
        if (a >= 10000000) return "S";   // [스멜3] 1000만원=S — 기준 숫자의 의미가 코드에 없음
        else if (a >= 1000000) return "A";   // 100만원=A
        else if (a >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
