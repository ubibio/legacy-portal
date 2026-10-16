package kr.hull.portal.approval;

import kr.hull.portal.common.FileAuditLogger;
import kr.hull.portal.common.SmtpMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [특성화 테스트 - 안전망 재점검] JaCoCo 로 확인한 남은 미검증 분기 + 아직 고정 안 된 현재 동작.
 *
 * - processApproval: 결재자 · 기안자가 비었거나 사용자 테이블에 없는 경우(남은 분기 6개),
 *   기안자가 아닌 사람의 상신, 기안자 본인의 반려, 남이 하는 상신 전 취소
 * - create: 긴급(urgent) 생성 시 우선순위 3 (남은 분기 1개)
 * - statusLabel · amountGrade: 대표값 · 경계값
 * - 기대값은 현재 코드를 실행해 관찰한 값이다. 기존 ApprovalServiceTest 6개는 건드리지 않는다.
 */
@DataJpaTest
@Import({ApprovalService.class, ApprovalNotifier.class, ApprovalAuditRecorder.class, SmtpMailSender.class, FileAuditLogger.class})
class ApprovalServiceSafetyNetTest {

    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;
    private static final int EXPENSE_TYPE = 1;
    private static final int NORMAL_PRIORITY = 2;
    private static final long MISSING_USER_ID = 9_999L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    private User drafter;
    private User teamLeader;

    @BeforeEach
    void saveUsers() {
        drafter = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
    }

    private Approval createApproval(Long drafterId, Long approverId) {
        return approvalService.create("노트북 구매", "개발용 노트북", EXPENSE_TYPE, NORMAL_PRIORITY,
                drafterId, approverId, 1_200_000L, false);
    }

    private Approval reload(Approval approval) {
        return approvalRepository.findById(approval.getId()).orElseThrow();
    }

    // ── 상신 ────────────────────────────────────────────────────────────────

    @Test
    void 기안자가_아닌_사람도_상신할_수_있다() {
        Approval approval = createApproval(drafter.getId(), teamLeader.getId());

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 1, null);

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    @Test
    void 결재자가_사용자_테이블에_없어도_상신은_되고_예외가_없다() {
        Approval approval = createApproval(drafter.getId(), MISSING_USER_ID);

        assertThatCode(() -> approvalService.processApproval(approval.getId(), drafter.getId(), 1, null))
                .doesNotThrowAnyException();

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    // ── 고액 지출 우선순위 · 긴급 ──────────────────────────────────────────────

    @Test
    void 긴급으로_생성하면_요청한_우선순위와_상관없이_3이다() {
        Approval approval = approvalService.create("서버 장애 대응", "긴급 구매", EXPENSE_TYPE, 1,
                drafter.getId(), teamLeader.getId(), 50_000L, true);

        assertThat(reload(approval).getPriority()).isEqualTo(3);
        assertThat(reload(approval).getStatus()).isZero();
    }

    // ── 승인 · 반려에서 결재자 · 기안자가 비었거나 없는 경우 ──────────────────────

    @Test
    void 결재자가_지정되지_않은_결재를_상신하면_상태는_상신으로_바뀌고_결재자_조회에서_예외가_난다() {
        Approval approval = createApproval(drafter.getId(), null);

        // 관찰: 상태를 1 로 저장한 뒤, 메일 수신자를 찾는 userRepository.findById(null) 에서 예외
        assertThatThrownBy(() -> approvalService.processApproval(approval.getId(), drafter.getId(), 1, null))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    /** 결재자 미지정 결재는 정상 흐름으로 상신(1)에 도달하지 못하므로(위 테스트), 상태를 직접 1 로 맞춰 둔다. */
    private Approval createSubmittedWithoutApprover() {
        Approval approval = createApproval(drafter.getId(), null);
        approval.setStatus(1);
        return approvalRepository.save(approval);
    }

    @Test
    void 결재자가_지정되지_않은_결재를_승인하면_조용히_무시된다() {
        Approval approval = createSubmittedWithoutApprover();

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
    }

    @Test
    void 기안자가_사용자_테이블에_없어도_승인은_된다() {
        Approval approval = createApproval(MISSING_USER_ID, teamLeader.getId());
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 1, null);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThat(reload(approval).getStatus()).isEqualTo(2);
    }

    @Test
    void 결재자가_지정되지_않은_결재를_반려하면_조용히_무시된다() {
        Approval approval = createSubmittedWithoutApprover();

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "예산 초과");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    @Test
    void 기안자가_사용자_테이블에_없어도_반려는_되고_사유가_저장된다() {
        Approval approval = createApproval(MISSING_USER_ID, teamLeader.getId());
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 1, null);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "예산 초과");

        assertThat(reload(approval).getStatus()).isEqualTo(3);
        assertThat(reload(approval).getRejectReason()).isEqualTo("예산 초과");
    }

    // ── 결재자가 아닌 사람의 반려 ──────────────────────────────────────────────

    @Test
    void 기안자_본인이_반려하면_조용히_무시된다() {
        Approval approval = createApproval(drafter.getId(), teamLeader.getId());
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        approvalService.processApproval(approval.getId(), drafter.getId(), 3, "내가 반려");

        assertThat(reload(approval).getStatus()).isEqualTo(1);
        assertThat(reload(approval).getRejectReason()).isNull();
    }

    // ── 상신 전 취소 ──────────────────────────────────────────────────────────

    @Test
    void 상신_전_결재를_기안자가_아닌_사람이_취소하면_조용히_무시된다() {
        Approval approval = createApproval(drafter.getId(), teamLeader.getId());

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isZero();
    }

    @Test
    void 기안자가_지정되지_않은_결재를_취소하면_조용히_무시된다() {
        Approval approval = createApproval(null, teamLeader.getId());

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(reload(approval).getStatus()).isZero();
    }

    // ── 상태 라벨 · 금액 등급 ──────────────────────────────────────────────────

    @ParameterizedTest(name = "status {0} → {1}")
    @CsvSource({"0, 임시저장", "1, 상신", "2, 승인", "3, 반려", "9, 취소"})
    void 상태_코드별_라벨을_돌려준다(int statusCode, String expectedLabel) {
        Approval approval = new Approval();
        approval.setStatus(statusCode);

        assertThat(approvalService.statusLabel(approval)).isEqualTo(expectedLabel);
    }

    @ParameterizedTest(name = "{0}원 → {1}")
    @CsvSource({"10000000, S", "9999999, A", "1000000, A", "999999, B", "100000, B", "99999, C"})
    void 금액_경계값별_등급을_돌려준다(long amount, String expectedGrade) {
        Approval approval = new Approval();
        approval.setAmount(amount);

        assertThat(approvalService.amountGrade(approval)).isEqualTo(expectedGrade);
    }
}
