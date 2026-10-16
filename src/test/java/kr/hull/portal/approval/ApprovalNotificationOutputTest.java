package kr.hull.portal.approval;

import kr.hull.portal.common.FileAuditLogger;
import kr.hull.portal.common.SmtpMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [특성화 테스트 - 메일 · 감사로그 출력] 결재 처리 때 콘솔로 나가는 메일 · 감사로그 문자열을 고정한다.
 *
 * - 메일 · 로그 협력 객체를 주입 방식으로 바꾸고(R02 · R03) 본문 조립을 공통화할 때 문구가 1글자도 바뀌지 않았는지 지키는 안전망.
 * - 기대값은 현재 코드의 실제 출력이다. 감사로그의 시각 부분만 실행마다 달라 "] 동작 id=.. by=.." 이후를 비교한다.
 */
@DataJpaTest
@Import({ApprovalService.class, ApprovalNotifier.class, ApprovalAuditRecorder.class, SmtpMailSender.class, FileAuditLogger.class})
@ExtendWith(OutputCaptureExtension.class)
class ApprovalNotificationOutputTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private UserRepository userRepository;

    private User drafter;
    private User teamLeader;

    @BeforeEach
    void saveUsers() {
        drafter = userRepository.save(new User("김사원", "kim@hull.kr", 1, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", 2, "개발1팀"));
    }

    private Approval createApproval() {
        return approvalService.create("노트북 구매", "개발용 노트북", 1, 2,
                drafter.getId(), teamLeader.getId(), 1_200_000L, false);
    }

    private static String mail(String to, String subject, String body) {
        return "=== MAIL ===" + System.lineSeparator()
                + "TO: " + to + System.lineSeparator()
                + "SUBJECT: " + subject + System.lineSeparator()
                + body + System.lineSeparator()
                + "============";
    }

    @Test
    void 결재를_생성하면_유형이_붙은_감사로그가_남는다(CapturedOutput output) {
        Approval approval = createApproval();

        assertThat(output.getOut())
                .contains("] APPROVAL CREATE id=" + approval.getId() + " by=" + drafter.getId() + " type=1" + System.lineSeparator())
                .contains("[AUDIT] [")
                .doesNotContain("=== MAIL ===");
    }

    @Test
    void 상신하면_결재자에게_결재요청_메일과_감사로그가_나간다(CapturedOutput output) {
        Approval approval = createApproval();

        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        assertThat(output.getOut())
                .contains(mail("park@hull.kr", "[결재요청] 노트북 구매",
                        "안녕하세요 박팀장님,\n결재 요청이 도착했습니다.\n제목: 노트북 구매\n기안자ID: " + drafter.getId()))
                .contains("] APPROVAL SUBMIT id=" + approval.getId() + " by=" + drafter.getId() + System.lineSeparator());
    }

    @Test
    void 승인하면_기안자에게_결재승인_메일과_감사로그가_나간다(CapturedOutput output) {
        Approval approval = createApproval();
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThat(output.getOut())
                .contains(mail("kim@hull.kr", "[결재승인] 노트북 구매",
                        "안녕하세요 김사원님,\n결재가 승인되었습니다.\n제목: 노트북 구매"))
                .contains("] APPROVAL APPROVE id=" + approval.getId() + " by=" + teamLeader.getId() + System.lineSeparator());
    }

    @Test
    void 반려하면_기안자에게_사유가_담긴_결재반려_메일과_감사로그가_나간다(CapturedOutput output) {
        Approval approval = createApproval();
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 3, "예산 초과");

        assertThat(output.getOut())
                .contains(mail("kim@hull.kr", "[결재반려] 노트북 구매",
                        "안녕하세요 김사원님,\n결재가 반려되었습니다.\n제목: 노트북 구매\n사유: 예산 초과"))
                .contains("] APPROVAL REJECT id=" + approval.getId() + " by=" + teamLeader.getId() + System.lineSeparator());
    }

    @Test
    void 취소하면_메일_없이_감사로그만_남는다(CapturedOutput output) {
        Approval approval = createApproval();

        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(output.getOut())
                .contains("] APPROVAL CANCEL id=" + approval.getId() + " by=" + drafter.getId() + System.lineSeparator())
                .doesNotContain("=== MAIL ===");
    }

    @Test
    void 무시되는_처리는_메일도_감사로그도_남기지_않는다(CapturedOutput output) {
        Approval approval = createApproval();
        int outputLengthAfterCreate = output.getOut().length();

        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");   // 상신 전 승인 - 무시

        assertThat(output.getOut().substring(outputLengthAfterCreate))
                .doesNotContain("[AUDIT]")
                .doesNotContain("=== MAIL ===");
    }
}
