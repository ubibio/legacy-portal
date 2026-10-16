package kr.hull.portal.approval;

import kr.hull.portal.common.ConsoleAuditLogger;
import kr.hull.portal.common.ConsoleMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * [특성화 테스트] ApprovalService.processApproval(id, userId, action, reason) 의 현재(레거시) 동작 고정.
 *
 * - 목적: 리팩토링 전 안전망. "옳은 동작"이 아니라 "지금 이렇게 동작한다"는 관찰값을 그대로 적는다.
 * - 코드값은 레거시 정수 그대로 검증한다 (status 0 임시저장 · 1 상신 · 2 승인 · 3 반려 · 9 취소,
 *   action 1 상신 · 2 승인 · 3 반려 · 9 취소, role 1 사원 · 2 팀장).
 * - 리팩토링 중 이 테스트의 호출 · 기대값은 수정하지 않는다 (CLAUDE.md 특성화 테스트 불변 규칙).
 */
@DataJpaTest
@Import({ApprovalService.class, ApprovalNotifier.class, ApprovalAuditRecorder.class, ConsoleMailSender.class, ConsoleAuditLogger.class})
class ApprovalServiceTest {

    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    private User drafter;
    private User teamLeader;
    private User staffApprover;

    @BeforeEach
    void saveUsers() {
        drafter = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
        staffApprover = userRepository.save(new User("최사원", "choi@hull.kr", STAFF_ROLE, "개발2팀"));
    }

    /** 지출(type 1) · 보통(priority 2) · 120만원 결재를 임시저장(status 0)으로 만든다. */
    private Approval createExpenseApproval(User approver) {
        return approvalService.create("노트북 구매", "개발용 노트북", 1, 2,
                drafter.getId(), approver.getId(), 1_200_000L, false);
    }

    @Test
    void 기안자가_상신하고_팀장이_승인하면_승인된다() {
        Approval approval = createExpenseApproval(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        Approval submitted = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(submitted.getStatus()).isEqualTo(1);
        assertThat(submitted.getPriority()).isEqualTo(3);   // 지출 · 100만원 이상 상신 시 우선순위 3으로 상향

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");
        Approval approved = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(2);
    }

    @Test
    void 팀장이_상신된_결재를_반려하면_반려되고_사유가_저장된다() {
        Approval approval = createExpenseApproval(teamLeader);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "예산 초과");

        Approval rejected = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(3);
        assertThat(rejected.getRejectReason()).isEqualTo("예산 초과");
    }

    @Test
    void 기안자가_임시저장_결재를_취소하면_취소된다() {
        Approval approval = createExpenseApproval(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        Approval canceled = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(9);
    }

    @Test
    void 결재자로_지정된_사원이_승인하면_조용히_무시된다() {
        Approval approval = createExpenseApproval(staffApprover);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        assertThatCode(() -> approvalService.processApproval(approval.getId(), staffApprover.getId(), 2, ""))
                .doesNotThrowAnyException();

        Approval unchanged = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(1);   // 권한(role 2 이상) 없음 - 상신 상태 그대로
    }

    @Test
    void 없는_결재_id로_처리하면_예외없이_무시된다() {
        Approval approval = createExpenseApproval(teamLeader);
        long missingApprovalId = approval.getId() + 999L;

        assertThatCode(() -> approvalService.processApproval(missingApprovalId, teamLeader.getId(), 2, ""))
                .doesNotThrowAnyException();

        assertThat(approvalRepository.findById(missingApprovalId)).isEmpty();
        assertThat(approvalRepository.findById(approval.getId()).orElseThrow().getStatus()).isZero();
    }

    @Test
    void 이미_승인된_결재를_다시_승인하면_무시되고_승인상태가_유지된다() {
        Approval approval = createExpenseApproval(teamLeader);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThatCode(() -> approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, ""))
                .doesNotThrowAnyException();

        Approval stillApproved = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(stillApproved.getStatus()).isEqualTo(2);
    }
}
