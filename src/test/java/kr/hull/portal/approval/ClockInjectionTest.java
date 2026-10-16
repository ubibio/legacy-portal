package kr.hull.portal.approval;

import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.common.ConsoleAuditLogger;
import kr.hull.portal.common.FakeMailSender;
import kr.hull.portal.notice.Notice;
import kr.hull.portal.notice.NoticeRepository;
import kr.hull.portal.notice.NoticeService;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [REQ-NF-004] 시각 주입 - 고정 Clock 을 넣으면 생성 · 수정 시각과 감사로그 시각이 정확히 그 값이 된다.
 *
 * - LocalDateTime.now() 를 직접 부르던 레거시에서는 "null 이 아니다" 정도로만 확인할 수 있던 값이다.
 * - 서비스는 직접 new 로 조립해 어떤 Clock 이 들어갔는지 테스트 안에서 보이게 한다 (ApprovalServiceMailUnitTest 와 같은 방식).
 */
@DataJpaTest
@ExtendWith(OutputCaptureExtension.class)
class ClockInjectionTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 10, 17, 9, 30, 0);
    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;
    private static final int SUBMIT_ACTION = 1;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private UserRepository userRepository;

    private Clock fixedClock;
    private ApprovalService approvalService;
    private NoticeService noticeService;
    private User drafter;
    private User teamLeader;

    @BeforeEach
    void assembleServicesWithFixedClock() {
        ZoneId zone = ZoneId.systemDefault();
        fixedClock = Clock.fixed(FIXED_TIME.atZone(zone).toInstant(), zone);
        AuditLogger auditLogger = new ConsoleAuditLogger(fixedClock);
        FakeMailSender fakeMailSender = new FakeMailSender();
        approvalService = new ApprovalService(approvalRepository, userRepository,
                new ApprovalNotifier(userRepository, fakeMailSender), new ApprovalAuditRecorder(auditLogger), fixedClock);
        noticeService = new NoticeService(noticeRepository, userRepository, fakeMailSender, auditLogger, fixedClock);

        drafter = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
    }

    @Test
    void 고정_Clock이면_기안의_생성시각과_수정시각이_고정_시각이다() {
        Approval approval = approvalService.create("노트북 구매", "개발용", 1, 2,
                drafter.getId(), teamLeader.getId(), 1_200_000L, false);

        assertThat(approval.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(approval.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void 고정_Clock이면_상신_후_수정시각도_고정_시각이다() {
        Approval approval = approvalService.create("노트북 구매", "개발용", 1, 2,
                drafter.getId(), teamLeader.getId(), 1_200_000L, false);

        approvalService.processApproval(approval.getId(), drafter.getId(), SUBMIT_ACTION, null);

        Approval reloaded = approvalRepository.findById(approval.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(1);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void 고정_Clock이면_감사로그_시각이_고정_시각이다(CapturedOutput output) {
        Approval approval = approvalService.create("노트북 구매", "개발용", 1, 2,
                drafter.getId(), teamLeader.getId(), 1_200_000L, false);

        assertThat(output.getOut())
                .contains("[AUDIT] [2026-10-17 09:30:00] APPROVAL CREATE id=" + approval.getId());
    }

    @Test
    void 고정_Clock이면_공지_작성시각이_고정_시각이다() {
        Notice notice = noticeService.create("점검 안내", "토요일 점검", 1, teamLeader.getId(), false);

        assertThat(notice.getCreatedAt()).isEqualTo(FIXED_TIME);
    }
}
