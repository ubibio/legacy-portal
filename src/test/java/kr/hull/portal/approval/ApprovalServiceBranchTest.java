package kr.hull.portal.approval;

import kr.hull.portal.common.FileAuditLogger;
import kr.hull.portal.common.SmtpMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * [특성화 테스트 - 분기 보강] processApproval 의 안 덮인 분기를 현재(레거시) 동작 그대로 고정한다.
 *
 * - 근거: docs/특성화 테스트 결과.md 의 안 덮인 분기 U1~U19 중 우선순위 1~6 (+ 우선순위 상향 경계값).
 * - 기존 ApprovalServiceTest 6개는 건드리지 않고 이 클래스로 따로 추가한다 (안전망 불변 규칙).
 * - 코드값은 레거시 정수 그대로 검증한다 (status 0 임시저장 · 1 상신 · 2 승인 · 3 반려 · 9 취소,
 *   action 1 상신 · 2 승인 · 3 반려 · 9 취소, type 1 지출 · 3 구매, priority 2 보통 · 3 높음).
 */
@DataJpaTest
@Import({ApprovalService.class, ApprovalNotifier.class, ApprovalAuditRecorder.class, SmtpMailSender.class, FileAuditLogger.class})
class ApprovalServiceBranchTest {

    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;
    private static final int EXPENSE_TYPE = 1;
    private static final int PURCHASE_TYPE = 3;
    private static final int NORMAL_PRIORITY = 2;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    private User drafter;
    private User teamLeader;
    private User otherTeamLeader;
    private User staffApprover;

    @BeforeEach
    void saveUsers() {
        drafter = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
        otherTeamLeader = userRepository.save(new User("정팀장", "jung@hull.kr", TEAM_LEADER_ROLE, "개발2팀"));
        staffApprover = userRepository.save(new User("최사원", "choi@hull.kr", STAFF_ROLE, "개발2팀"));
    }

    private Approval createApproval(int type, long amount, User approver) {
        return approvalService.create("노트북 구매", "개발용 노트북", type, NORMAL_PRIORITY,
                drafter.getId(), approver.getId(), amount, false);
    }

    private Approval createSubmittedExpense(User approver) {
        Approval approval = createApproval(EXPENSE_TYPE, 1_200_000L, approver);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        return approval;
    }

    private Approval reload(Approval approval) {
        return approvalRepository.findById(approval.getId()).orElseThrow();
    }

    // ── 우선순위 1: 지정된 결재자가 아닌 팀장 (U8 · U12) ─────────────────────

    @Test
    void 지정된_결재자가_아닌_팀장이_승인하면_조용히_무시된다() {
        Approval approval = createSubmittedExpense(teamLeader);

        approvalService.processApproval(approval.getId(), otherTeamLeader.getId(), 2, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    @Test
    void 지정된_결재자가_아닌_팀장이_반려하면_조용히_무시되고_사유도_저장되지_않는다() {
        Approval approval = createSubmittedExpense(teamLeader);

        approvalService.processApproval(approval.getId(), otherTeamLeader.getId(), 3, "예산 초과");

        Approval unchanged = reload(approval);
        assertThat(unchanged.getStatus()).isEqualTo(1);
        assertThat(unchanged.getRejectReason()).isNull();
    }

    // ── 우선순위 2: 취소 분기 (U15 · U16 · U19) ─────────────────────────────

    @Test
    void 기안자가_상신된_결재를_취소하면_취소된다() {
        Approval approval = createSubmittedExpense(teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isEqualTo(9);
    }

    @Test
    void 기안자가_승인된_결재를_취소하면_조용히_무시된다() {
        Approval approval = createSubmittedExpense(teamLeader);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isEqualTo(2);
    }

    @Test
    void 기안자가_아닌_결재자가_취소하면_조용히_무시된다() {
        Approval approval = createSubmittedExpense(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    // ── 우선순위 3: 반려 쪽 권한 · 상태 (U13 · U10) ─────────────────────────

    @Test
    void 결재자로_지정된_사원이_반려하면_조용히_무시된다() {
        Approval approval = createSubmittedExpense(staffApprover);

        approvalService.processApproval(approval.getId(), staffApprover.getId(), 3, "예산 초과");

        Approval unchanged = reload(approval);
        assertThat(unchanged.getStatus()).isEqualTo(1);
        assertThat(unchanged.getRejectReason()).isNull();
    }

    @Test
    void 임시저장_결재를_반려하면_조용히_무시된다() {
        Approval approval = createApproval(EXPENSE_TYPE, 1_200_000L, teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "예산 초과");

        Approval unchanged = reload(approval);
        assertThat(unchanged.getStatus()).isZero();
        assertThat(unchanged.getRejectReason()).isNull();
    }

    // ── 우선순위 4: 상신 시 우선순위 상향 경계 (U4 · U5 + 경계값) ────────────

    @Test
    void 지출_100만원_미만을_상신하면_우선순위는_그대로다() {
        Approval approval = createApproval(EXPENSE_TYPE, 999_999L, teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        Approval submitted = reload(approval);
        assertThat(submitted.getStatus()).isEqualTo(1);
        assertThat(submitted.getPriority()).isEqualTo(2);
    }

    @Test
    void 지출_정확히_100만원을_상신하면_우선순위가_3으로_오른다() {
        Approval approval = createApproval(EXPENSE_TYPE, 1_000_000L, teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        Approval submitted = reload(approval);
        assertThat(submitted.getStatus()).isEqualTo(1);
        assertThat(submitted.getPriority()).isEqualTo(3);
    }

    @Test
    void 지출이_아닌_고액_결재를_상신하면_우선순위는_그대로다() {
        Approval approval = createApproval(PURCHASE_TYPE, 5_000_000L, teamLeader);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        Approval submitted = reload(approval);
        assertThat(submitted.getStatus()).isEqualTo(1);
        assertThat(submitted.getPriority()).isEqualTo(2);
    }

    // ── 우선순위 5: 재상신 (U3) ─────────────────────────────────────────────

    @Test
    void 이미_상신된_결재를_다시_상신하면_조용히_무시되고_수정시각도_그대로다() {
        Approval approval = createSubmittedExpense(teamLeader);
        LocalDateTime submittedAt = reload(approval).getUpdatedAt();

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        Approval unchanged = reload(approval);
        assertThat(unchanged.getStatus()).isEqualTo(1);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(submittedAt);
    }

    // ── 우선순위 6: 정의되지 않은 action · 없는 사용자 (U2 · U1) ──────────────

    @Test
    void 정의되지_않은_action은_예외없이_무시된다() {
        Approval approval = createApproval(EXPENSE_TYPE, 1_200_000L, teamLeader);

        assertThatCode(() -> approvalService.processApproval(approval.getId(), drafter.getId(), 5, null))
                .doesNotThrowAnyException();

        assertThat(reload(approval).getStatus()).isZero();
    }

    @Test
    void 없는_사용자_id로_처리하면_예외없이_무시된다() {
        Approval approval = createApproval(EXPENSE_TYPE, 1_200_000L, teamLeader);
        long missingUserId = staffApprover.getId() + 999L;

        assertThatCode(() -> approvalService.processApproval(approval.getId(), missingUserId, 1, null))
                .doesNotThrowAnyException();

        assertThat(reload(approval).getStatus()).isZero();
    }
}
