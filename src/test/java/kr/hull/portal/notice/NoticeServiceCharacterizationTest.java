package kr.hull.portal.notice;

import kr.hull.portal.common.ConsoleAuditLogger;
import kr.hull.portal.common.ConsoleMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [특성화 테스트 - 공지] NoticeService 의 현재 동작을 그대로 고정한다 (안전망).
 *
 * - 목적: 공지 서비스 리팩토링(감사로그 · 메일 양식 공통화, 매직넘버 enum 화, 로직을 엔티티로 이동) 전에
 *   "지금 이렇게 동작한다"를 박제한다. 옳고 그름은 판단하지 않는다 - 결함도 현재 동작이면 그대로 고정한다.
 * - 기대값은 현재 코드를 실행해 관찰한 값이다. 리팩토링 중 이 파일의 호출 · 기대값은 수정하지 않는다 (CLAUDE.md).
 * - 정수 리터럴로 호출 · 검증한다: status 0 임시 · 1 게시 · 9 내림, category 1 일반 · 2 긴급 · 3 인사, role 1 사원 · 2 팀장 · 3 임원.
 * - 메일 · 감사로그는 콘솔 출력 문자열을 1글자까지 비교한다. 감사로그의 시각 부분만 실행마다 달라 "] 동작 id=.. by=.." 이후를 비교한다.
 * - DB 저장값은 엔티티를 거치지 않고 JDBC 로 컬럼을 직접 읽는다 - 나중에 enum 을 도입해도 0/1/9 가 그대로 저장되는지 지킨다.
 */
@DataJpaTest
@Import({NoticeService.class, ConsoleMailSender.class, ConsoleAuditLogger.class})
@ExtendWith(OutputCaptureExtension.class)
class NoticeServiceCharacterizationTest {

    private static final int STAFF_ROLE = 1;
    private static final int TEAM_LEADER_ROLE = 2;
    private static final int EXECUTIVE_ROLE = 3;
    private static final int GENERAL_CATEGORY = 1;
    private static final int EMERGENCY_CATEGORY = 2;
    private static final int PERSONNEL_CATEGORY = 3;
    private static final long MISSING_ID = 9_999L;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User staff;
    private User teamLeader;
    private User executive;

    @BeforeEach
    void saveUsers() {
        staff = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", TEAM_LEADER_ROLE, "개발1팀"));
        executive = userRepository.save(new User("이임원", "lee@hull.kr", EXECUTIVE_ROLE, "경영지원"));
    }

    private Notice createNotice(int category) {
        return noticeService.create("서버 점검 안내", "토요일 02시 점검", category, teamLeader.getId(), false);
    }

    private int storedStatus(Long noticeId) {
        entityManager.flush();   // 아직 DB 에 반영되지 않은 변경을 내보낸 뒤 컬럼을 읽는다
        return jdbcTemplate.queryForObject("select status from notice where id = ?", Integer.class, noticeId);
    }

    private int reloadedStatus(Long noticeId) {
        return noticeRepository.findById(noticeId).orElseThrow().getStatus();
    }

    private static String mail(String to, String subject, String body) {
        return "=== MAIL ===" + System.lineSeparator()
                + "TO: " + to + System.lineSeparator()
                + "SUBJECT: " + subject + System.lineSeparator()
                + body + System.lineSeparator()
                + "============";
    }

    private static int countOccurrences(String text, String keyword) {
        return text.split(java.util.regex.Pattern.quote(keyword), -1).length - 1;
    }

    // ── create ─────────────────────────────────────────────────────────

    @Test
    void 공지를_생성하면_임시_상태0으로_입력값_그대로_저장된다() {
        Notice notice = noticeService.create("서버 점검 안내", "토요일 02시 점검", PERSONNEL_CATEGORY, teamLeader.getId(), true);

        Notice reloaded = noticeRepository.findById(notice.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(0);
        assertThat(reloaded.getTitle()).isEqualTo("서버 점검 안내");
        assertThat(reloaded.getContent()).isEqualTo("토요일 02시 점검");
        assertThat(reloaded.getCategory()).isEqualTo(3);
        assertThat(reloaded.getWriterId()).isEqualTo(teamLeader.getId());
        assertThat(reloaded.isPinned()).isTrue();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(storedStatus(notice.getId())).isEqualTo(0);
    }

    @Test
    void 공지를_생성하면_감사로그만_남고_메일은_나가지_않는다(CapturedOutput output) {
        Notice notice = createNotice(EMERGENCY_CATEGORY);

        assertThat(output.getOut())
                .contains("[AUDIT] [")
                .contains("] NOTICE CREATE id=" + notice.getId() + " by=" + teamLeader.getId() + System.lineSeparator())
                .doesNotContain("=== MAIL ===");
    }

    @Test
    void 작성자가_없는_사용자여도_생성된다() {
        // [보존 대상] 레거시는 작성자 존재 여부 · 권한을 확인하지 않는다 (사원도 · 없는 id 도 생성 가능)
        Notice notice = noticeService.create("제목", "내용", GENERAL_CATEGORY, MISSING_ID, false);

        assertThat(notice.getId()).isNotNull();
        assertThat(reloadedStatus(notice.getId())).isEqualTo(0);
    }

    // ── publish: 권한 · 상태 ─────────────────────────────────────────────

    @Test
    void 팀장이_임시_공지를_게시하면_상태1로_저장된다() {
        Notice notice = createNotice(GENERAL_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reloadedStatus(notice.getId())).isEqualTo(1);
        assertThat(storedStatus(notice.getId())).isEqualTo(1);
    }

    @Test
    void 임원도_게시할_수_있다() {
        Notice notice = createNotice(GENERAL_CATEGORY);

        noticeService.publish(notice.getId(), executive.getId());

        assertThat(reloadedStatus(notice.getId())).isEqualTo(1);
    }

    @Test
    void 사원이_게시하면_조용히_무시되고_상태0_그대로다(CapturedOutput output) {
        Notice notice = createNotice(EMERGENCY_CATEGORY);

        assertThatCode(() -> noticeService.publish(notice.getId(), staff.getId())).doesNotThrowAnyException();

        assertThat(reloadedStatus(notice.getId())).isEqualTo(0);
        assertThat(output.getOut()).doesNotContain("NOTICE PUBLISH").doesNotContain("=== MAIL ===");
    }

    @Test
    void 이미_게시된_공지를_다시_게시하면_무시되고_로그도_한번만_남는다(CapturedOutput output) {
        Notice notice = createNotice(GENERAL_CATEGORY);
        noticeService.publish(notice.getId(), teamLeader.getId());

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reloadedStatus(notice.getId())).isEqualTo(1);
        assertThat(countOccurrences(output.getOut(), "NOTICE PUBLISH")).isEqualTo(1);
    }

    @Test
    void 내림_상태9_공지는_게시되지_않는다() {
        // 내림(9) 으로 만드는 서비스 메서드가 없어 저장소로 직접 준비한다
        Notice notice = createNotice(GENERAL_CATEGORY);
        notice.setStatus(9);
        noticeRepository.save(notice);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(reloadedStatus(notice.getId())).isEqualTo(9);
    }

    @Test
    void 없는_공지_id로_게시하면_예외없이_무시된다() {
        assertThatCode(() -> noticeService.publish(MISSING_ID, teamLeader.getId())).doesNotThrowAnyException();
    }

    @Test
    void 없는_사용자가_게시하면_예외없이_무시된다() {
        Notice notice = createNotice(GENERAL_CATEGORY);

        assertThatCode(() -> noticeService.publish(notice.getId(), MISSING_ID)).doesNotThrowAnyException();

        assertThat(reloadedStatus(notice.getId())).isEqualTo(0);
    }

    @Test
    void 공지_id가_null이면_저장소에서_예외가_난다() {
        // [보존 대상 · 결함 후보] null 을 걸러내지 않아 findById(null) 이 예외를 던진다 (재설계 단계에서 다룸)
        assertThatThrownBy(() -> noticeService.publish(null, teamLeader.getId()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    // ── publish: 메일 · 감사로그 출력 ─────────────────────────────────────

    @Test
    void 게시하면_게시_감사로그가_남는다(CapturedOutput output) {
        Notice notice = createNotice(GENERAL_CATEGORY);

        noticeService.publish(notice.getId(), executive.getId());

        assertThat(output.getOut())
                .contains("] NOTICE PUBLISH id=" + notice.getId() + " by=" + executive.getId() + System.lineSeparator());
    }

    @Test
    void 일반_공지를_게시하면_메일은_나가지_않는다(CapturedOutput output) {
        Notice notice = createNotice(GENERAL_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(output.getOut()).doesNotContain("=== MAIL ===");
    }

    @Test
    void 인사_공지를_게시해도_메일은_나가지_않는다(CapturedOutput output) {
        Notice notice = createNotice(PERSONNEL_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        assertThat(output.getOut()).doesNotContain("=== MAIL ===");
    }

    @Test
    void 긴급_공지를_게시하면_전체_사용자에게_긴급공지_메일이_한통씩_나간다(CapturedOutput output) {
        Notice notice = createNotice(EMERGENCY_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        String printed = output.getOut();
        assertThat(countOccurrences(printed, "=== MAIL ===")).isEqualTo(3);
        assertThat(printed)
                .contains(mail("kim@hull.kr", "[긴급공지] 서버 점검 안내",
                        "안녕하세요 김사원님,\n긴급 공지가 게시되었습니다.\n제목: 서버 점검 안내"))
                .contains(mail("park@hull.kr", "[긴급공지] 서버 점검 안내",
                        "안녕하세요 박팀장님,\n긴급 공지가 게시되었습니다.\n제목: 서버 점검 안내"))
                .contains(mail("lee@hull.kr", "[긴급공지] 서버 점검 안내",
                        "안녕하세요 이임원님,\n긴급 공지가 게시되었습니다.\n제목: 서버 점검 안내"));
    }

    @Test
    void 긴급_공지는_메일을_모두_보낸_뒤에_게시_감사로그를_남긴다(CapturedOutput output) {
        Notice notice = createNotice(EMERGENCY_CATEGORY);

        noticeService.publish(notice.getId(), teamLeader.getId());

        String printed = output.getOut();
        assertThat(printed.indexOf("NOTICE PUBLISH")).isGreaterThan(printed.lastIndexOf("=== MAIL ==="));
    }

    // ── statusLabel ───────────────────────────────────────────────────

    @Test
    void 상태_라벨은_0임시_1게시_9내림_그밖은_알수없음이다() {
        Notice notice = new Notice();

        notice.setStatus(0);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("임시");
        notice.setStatus(1);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("게시");
        notice.setStatus(9);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("내림");
        notice.setStatus(2);
        assertThat(noticeService.statusLabel(notice)).isEqualTo("알수없음");
    }
}
