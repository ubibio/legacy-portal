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
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [특성화 테스트 - processApproval 빈틈 보강] 기존 결재 특성화 테스트 41개(ApprovalServiceTest · BranchTest · SafetyNetTest)가
 * 아직 고정하지 않은 processApproval 동작을 지금 코드 그대로 고정한다 (다음 변경 전 안전망).
 *
 * - 보강한 것: 임원 승인 · 반려 사유 null/빈 문자열 · 끝난 상태(반려 3 · 취소 9 · 승인 2)에서의 처리 ·
 *   승인 · 반려 · 취소 때 수정시각 · null id / null 사용자 id 예외 · 여러 정의되지 않은 action 값 ·
 *   새 진입점 processApproval(id, ProcessApprovalCommand) 가 레거시 4인자 호출과 같은 결과인지.
 * - 기대값은 현재 코드를 실행해 관찰한 값이다. 이상해 보여도(예: null id 예외) 지금 동작 그대로 기록한다.
 * - 정수 리터럴로 호출 · 검증한다: action 1 상신 · 2 승인 · 3 반려 · 9 취소, status 0 · 1 · 2 · 3 · 9, role 1 사원 · 2 팀장 · 3 임원.
 * - 리팩토링 중 이 파일의 호출 · 기대값은 수정하지 않는다 (CLAUDE.md 특성화 테스트 불변 규칙).
 */
@DataJpaTest
@Import({ApprovalService.class, ApprovalNotifier.class, ApprovalAuditRecorder.class, ConsoleMailSender.class, ConsoleAuditLogger.class})
class ApprovalProcessCharacterizationTest {

    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;
    private static final int EXECUTIVE_ROLE = 3;
    private static final int EXPENSE_TYPE = 1;
    private static final int NORMAL_PRIORITY = 2;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private User drafter;
    private User teamLeader;
    private User executive;

    @BeforeEach
    void saveUsers() {
        drafter = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
        executive = userRepository.save(new User("이임원", "lee@hull.kr", EXECUTIVE_ROLE, "경영지원"));
    }

    private Approval createApproval(User approver) {
        return approvalService.create("노트북 구매", "개발용 노트북", EXPENSE_TYPE, NORMAL_PRIORITY,
                drafter.getId(), approver.getId(), 500_000L, false);
    }

    private Approval createSubmitted(User approver) {
        Approval approval = createApproval(approver);
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        return approval;
    }

    private Approval reload(Approval approval) {
        return approvalRepository.findById(approval.getId()).orElseThrow();
    }

    // ── 권한 상한: 임원 ─────────────────────────────────────────────────

    @Test
    void 결재자로_지정된_임원이_승인하면_승인된다() {
        Approval approval = createSubmitted(executive);

        approvalService.processApproval(approval.getId(), executive.getId(), 2, null);

        assertThat(reload(approval).getStatus()).isEqualTo(2);
    }

    @Test
    void 결재자로_지정된_임원이_반려하면_반려된다() {
        Approval approval = createSubmitted(executive);

        approvalService.processApproval(approval.getId(), executive.getId(), 3, "보류");

        assertThat(reload(approval).getStatus()).isEqualTo(3);
        assertThat(reload(approval).getRejectReason()).isEqualTo("보류");
    }

    // ── 반려 사유 ─────────────────────────────────────────────────────

    @Test
    void 반려_사유가_null이면_null_그대로_저장된다() {
        // [보존 대상] 서비스는 사유를 검증하지 않는다 (API 로 reason 을 빼면 DTO 가 "" 로 채우지만, 서비스 직접 호출은 null 그대로)
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, null);

        assertThat(reload(approval).getStatus()).isEqualTo(3);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    @Test
    void 반려_사유가_빈_문자열이어도_반려된다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "");

        assertThat(reload(approval).getStatus()).isEqualTo(3);
        assertThat(reload(approval).getRejectReason()).isEqualTo("");
    }

    @Test
    void 반려_사유는_승인_요청에서는_무시된다() {
        Approval approval = createSubmitted(teamLeader);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "이 사유는 저장되지 않음");

        assertThat(reload(approval).getStatus()).isEqualTo(2);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    // ── 끝난 상태에서의 처리 (모두 조용히 무시) ───────────────────────────────

    @Test
    void 반려된_결재는_다시_상신_승인_반려_취소해도_반려상태와_사유가_유지된다() {
        Approval approval = createSubmitted(teamLeader);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "예산 초과");

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, null);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "다른 사유");
        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isEqualTo(3);
        assertThat(reload(approval).getRejectReason()).isEqualTo("예산 초과");
    }

    @Test
    void 취소된_결재는_다시_상신_승인_반려_취소해도_취소상태가_유지된다() {
        Approval approval = createApproval(teamLeader);
        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, null);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "사유");
        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isEqualTo(9);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    @Test
    void 승인된_결재는_반려하거나_다시_상신해도_승인상태가_유지된다() {
        Approval approval = createSubmitted(teamLeader);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, null);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "뒤늦은 반려");
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        assertThat(reload(approval).getStatus()).isEqualTo(2);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    // ── 수정 시각 ─────────────────────────────────────────────────────

    @Test
    void 승인_반려_취소에_성공하면_수정시각이_바뀐다() {
        Approval approved = createSubmitted(teamLeader);
        LocalDateTime beforeApprove = reload(approved).getUpdatedAt();
        Approval rejected = createSubmitted(teamLeader);
        LocalDateTime beforeReject = reload(rejected).getUpdatedAt();
        Approval canceled = createApproval(teamLeader);
        LocalDateTime beforeCancel = reload(canceled).getUpdatedAt();

        approvalService.processApproval(approved.getId(), teamLeader.getId(), 2, null);
        approvalService.processApproval(rejected.getId(), teamLeader.getId(), 3, "사유");
        approvalService.processApproval(canceled.getId(), drafter.getId(), 9, null);

        assertThat(reload(approved).getUpdatedAt()).isAfterOrEqualTo(beforeApprove);
        assertThat(reload(rejected).getUpdatedAt()).isAfterOrEqualTo(beforeReject);
        assertThat(reload(canceled).getUpdatedAt()).isAfterOrEqualTo(beforeCancel);
        assertThat(reload(approved).getCreatedAt()).isNotNull();
    }

    @Test
    void 무시된_처리는_수정시각을_바꾸지_않는다() {
        Approval approval = createSubmitted(teamLeader);
        LocalDateTime submittedAt = reload(approval).getUpdatedAt();

        approvalService.processApproval(approval.getId(), drafter.getId(), 2, null);   // 사원 승인 → 무시
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 9, null); // 기안자 아님 취소 → 무시

        assertThat(reload(approval).getUpdatedAt()).isEqualTo(submittedAt);
        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    // ── 정의되지 않은 action 값 ─────────────────────────────────────────

    @Test
    void 정의되지_않은_action_0_4_8_10_음수는_모두_예외없이_무시된다() {
        Approval approval = createApproval(teamLeader);

        for (int undefinedAction : new int[]{0, 4, 5, 6, 7, 8, 10, -1}) {
            assertThatCode(() -> approvalService.processApproval(approval.getId(), drafter.getId(), undefinedAction, null))
                    .doesNotThrowAnyException();
        }

        assertThat(reload(approval).getStatus()).isEqualTo(0);
    }

    // ── null 인자 (결함 후보 - 지금 동작 그대로) ────────────────────────────────

    @Test
    void 결재_id가_null이면_저장소에서_예외가_난다() {
        // [보존 대상 · 결함 후보] null 을 걸러내지 않아 findById(null) 이 예외를 던진다 (재설계 단계에서 다룸)
        assertThatThrownBy(() -> approvalService.processApproval(null, drafter.getId(), 1, null))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    void 사용자_id가_null이면_저장소에서_예외가_나고_상태는_그대로다() {
        // [보존 대상 · 결함 후보] 결재는 찾은 뒤 사용자 findById(null) 에서 예외
        Approval approval = createApproval(teamLeader);

        assertThatThrownBy(() -> approvalService.processApproval(approval.getId(), null, 1, null))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);

        assertThat(reload(approval).getStatus()).isEqualTo(0);
    }

    @Test
    void 없는_결재_id와_null_사용자_id를_함께_주면_예외없이_무시된다() {
        // [보존 대상] 결재가 없으면 사용자 조회 전에 리턴하므로 null 사용자 id 로도 예외가 나지 않는다 (검사 순서 고정)
        assertThatCode(() -> approvalService.processApproval(9_999L, null, 1, null)).doesNotThrowAnyException();
    }

    // ── 새 진입점 (명령 객체) ─────────────────────────────────────────────

    @Test
    void 명령_객체로_처리해도_레거시_4인자_호출과_같은_결과다() {
        Approval byLegacyCall = createApproval(teamLeader);
        Approval byCommand = createApproval(teamLeader);

        approvalService.processApproval(byLegacyCall.getId(), drafter.getId(), 1, null);
        approvalService.processApproval(byLegacyCall.getId(), teamLeader.getId(), 3, "예산 초과");
        approvalService.processApproval(byCommand.getId(), new ProcessApprovalCommand(drafter.getId(), 1, null));
        approvalService.processApproval(byCommand.getId(), new ProcessApprovalCommand(teamLeader.getId(), 3, "예산 초과"));

        assertThat(reload(byCommand).getStatus()).isEqualTo(reload(byLegacyCall).getStatus()).isEqualTo(3);
        assertThat(reload(byCommand).getRejectReason()).isEqualTo(reload(byLegacyCall).getRejectReason()).isEqualTo("예산 초과");
        assertThat(reload(byCommand).getPriority()).isEqualTo(reload(byLegacyCall).getPriority()).isEqualTo(2);
    }
}
