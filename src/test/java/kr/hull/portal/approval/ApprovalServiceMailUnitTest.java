package kr.hull.portal.approval;

import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.common.FakeMailSender;
import kr.hull.portal.common.FakeMailSender.SentMail;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * [단위 테스트 - 메일 발송 호출] ApprovalService 가 언제 · 누구에게 · 어떤 메일을 보내라고 "호출"하는지 검증한다.
 *
 * - 특성화 테스트(안전망)와 별개의 새 파일이다. 기존 테스트 6개 파일은 건드리지 않는다.
 * - 협력 객체를 테스트용으로 갈아끼운다 - MailSender · AuditLogger 인터페이스 추출 + 생성자 주입(R02 · R03) 덕분에 가능해졌다.
 *     MailSender  → FakeMailSender      : 실제 발송(콘솔 출력) 없이 send 호출 내용만 기록
 *     AuditLogger → mock(AuditLogger)   : Mockito 가짜 - write(동작, id, 행위자) 호출 인자만 검증 (줄 형식은 ConsoleAuditLogger 몫이라 여기선 안 봄)
 * - 저장소(ApprovalRepository · UserRepository)는 H2 진짜를 쓴다(@DataJpaTest).
 *   결재는 setter 로 상태를 꾸미지 않고 service.create · processApproval 로 실제 흐름대로 준비한다.
 * - 스프링 빈 조립(@Import) 대신 서비스를 직접 new 로 조립한다 - 어떤 가짜가 들어갔는지 테스트 안에서 바로 보이게.
 * - action 은 레거시 계약대로 정수(1 상신 · 2 승인 · 3 반려 · 9 취소)로 호출한다.
 */
@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
class ApprovalServiceMailUnitTest {

    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;
    private static final int SUBMIT_ACTION = 1;
    private static final int APPROVE_ACTION = 2;
    private static final int REJECT_ACTION = 3;
    private static final int CANCEL_ACTION = 9;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private FakeMailSender fakeMailSender;
    private AuditLogger auditLogger;
    private ApprovalService approvalService;

    private User drafter;
    private User teamLeader;
    private User staff;

    @BeforeEach
    void assembleServiceWithFakes() {
        fakeMailSender = new FakeMailSender();
        auditLogger = mock(AuditLogger.class);
        approvalService = new ApprovalService(approvalRepository, userRepository,
                new ApprovalNotifier(userRepository, fakeMailSender),
                new ApprovalAuditRecorder(auditLogger));

        drafter = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
        staff = userRepository.save(new User("최사원", "choi@hull.kr", STAFF_ROLE, "개발2팀"));
    }

    private Approval createApproval() {
        return approvalService.create("노트북 구매", "개발용 노트북", 1, 2,
                drafter.getId(), teamLeader.getId(), 1_200_000L, false);
    }

    private Approval createSubmittedApproval() {
        Approval approval = createApproval();
        approvalService.processApproval(approval.getId(), drafter.getId(), SUBMIT_ACTION, null);
        return approval;
    }

    private void verifyCreatedAndSubmitted(Approval approval) {
        verify(auditLogger).write("APPROVAL CREATE", approval.getId(), drafter.getId(), "type=1");
        verify(auditLogger).write("APPROVAL SUBMIT", approval.getId(), drafter.getId());
    }

    @Test
    void 생성만_하면_메일은_보내지_않고_감사로그만_한번_쓴다() {
        Approval approval = createApproval();

        assertThat(fakeMailSender.getSentMails()).isEmpty();
        verify(auditLogger).write("APPROVAL CREATE", approval.getId(), drafter.getId(), "type=1");
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void 상신하면_결재자에게_결재요청_메일을_한통_보낸다() {
        Approval approval = createSubmittedApproval();

        assertThat(fakeMailSender.getSentMails()).containsExactly(new SentMail(
                "park@hull.kr",
                "[결재요청] 노트북 구매",
                "안녕하세요 박팀장님,\n결재 요청이 도착했습니다.\n제목: 노트북 구매\n기안자ID: " + drafter.getId()));
        verifyCreatedAndSubmitted(approval);
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void 승인하면_기안자에게_결재승인_메일을_보낸다() {
        Approval approval = createSubmittedApproval();

        approvalService.processApproval(approval.getId(), teamLeader.getId(), APPROVE_ACTION, null);

        List<SentMail> sentMails = fakeMailSender.getSentMails();
        assertThat(sentMails).hasSize(2);
        assertThat(sentMails.get(1)).isEqualTo(new SentMail(
                "kim@hull.kr",
                "[결재승인] 노트북 구매",
                "안녕하세요 김사원님,\n결재가 승인되었습니다.\n제목: 노트북 구매"));
        verifyCreatedAndSubmitted(approval);
        verify(auditLogger).write("APPROVAL APPROVE", approval.getId(), teamLeader.getId());
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void 반려하면_기안자에게_사유가_담긴_결재반려_메일을_보낸다() {
        Approval approval = createSubmittedApproval();

        approvalService.processApproval(approval.getId(), teamLeader.getId(), REJECT_ACTION, "예산 초과");

        List<SentMail> sentMails = fakeMailSender.getSentMails();
        assertThat(sentMails).hasSize(2);
        assertThat(sentMails.get(1)).isEqualTo(new SentMail(
                "kim@hull.kr",
                "[결재반려] 노트북 구매",
                "안녕하세요 김사원님,\n결재가 반려되었습니다.\n제목: 노트북 구매\n사유: 예산 초과"));
        verifyCreatedAndSubmitted(approval);
        verify(auditLogger).write("APPROVAL REJECT", approval.getId(), teamLeader.getId());
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void 취소하면_메일은_보내지_않고_감사로그만_쓴다() {
        Approval approval = createApproval();

        approvalService.processApproval(approval.getId(), drafter.getId(), CANCEL_ACTION, null);

        assertThat(fakeMailSender.getSentMails()).isEmpty();
        verify(auditLogger).write("APPROVAL CREATE", approval.getId(), drafter.getId(), "type=1");
        verify(auditLogger).write("APPROVAL CANCEL", approval.getId(), drafter.getId());
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void 권한없는_사원이_승인하면_메일도_감사로그도_추가되지_않는다() {
        Approval approval = createSubmittedApproval();

        approvalService.processApproval(approval.getId(), staff.getId(), APPROVE_ACTION, null);

        // [보존 대상] 레거시 동작 - 권한 없는 승인은 조용히 무시 (상신 때의 메일 1통 · 로그 2줄에서 늘지 않는다)
        assertThat(fakeMailSender.getSentMails()).hasSize(1);
        verifyCreatedAndSubmitted(approval);
        verifyNoMoreInteractions(auditLogger);
    }

    @Test
    void 가짜_발송기를_주입하면_콘솔로_실제_메일이_나가지_않는다(CapturedOutput output) {
        Approval approval = createSubmittedApproval();
        approvalService.processApproval(approval.getId(), teamLeader.getId(), APPROVE_ACTION, null);

        assertThat(fakeMailSender.getSentMails()).hasSize(2);
        assertThat(output.getOut()).doesNotContain("=== MAIL ===").doesNotContain("[AUDIT]");
    }
}
