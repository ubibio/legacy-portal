package kr.hull.portal.schedule;

import kr.hull.portal.common.ConsoleAuditLogger;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [특성화 테스트 - 일정] ScheduleService 의 현재 동작을 그대로 고정한다 (안전망).
 *
 * - 목적: 일정 서비스 리팩토링(긴 검증 분리 · flag1 이름 · 겹침 판정을 엔티티로 · 매직넘버 enum 화 · 감사로그 공통화) 전에
 *   "지금 이렇게 동작한다"를 박제한다. 옳고 그름은 판단하지 않는다 - 결함도 현재 동작이면 그대로 고정한다.
 * - 기대값은 현재 코드를 실행해 관찰한 값이다. 리팩토링 중 이 파일의 호출 · 기대값은 수정하지 않는다 (CLAUDE.md).
 * - 정수 리터럴로 검증한다: status 0 예정 · 1 확정 · 9 취소.
 * - 겹침 경계는 "기존 시작 < 새 끝 && 새 시작 < 기존 끝" (끝과 시작이 맞닿으면 겹치지 않음) - 경계값으로 고정한다.
 * - 시각은 고정값(2026-11-02 10:00 기준)을 써서 실행할 때마다 같은 결과가 나오게 한다.
 */
@DataJpaTest
@Import({ScheduleService.class, ConsoleAuditLogger.class})
@ExtendWith(OutputCaptureExtension.class)
class ScheduleServiceCharacterizationTest {

    private static final int STAFF_ROLE = 1;
    private static final long MISSING_ID = 9_999L;
    private static final LocalDateTime TEN_O_CLOCK = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime ELEVEN_O_CLOCK = LocalDateTime.of(2026, 11, 2, 11, 0);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 2, 12, 0);

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User otherUser;

    @BeforeEach
    void saveUsers() {
        owner = userRepository.save(new User("김사원", "kim@hull.kr", STAFF_ROLE, "개발1팀"));
        otherUser = userRepository.save(new User("최사원", "choi@hull.kr", STAFF_ROLE, "개발2팀"));
    }

    private Schedule createTenToEleven() {
        return scheduleService.create("주간 회의", owner.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, "3층 회의실");
    }

    private int storedStatus(Long scheduleId) {
        entityManager.flush();   // 아직 DB 에 반영되지 않은 변경을 내보낸 뒤 컬럼을 읽는다
        return jdbcTemplate.queryForObject("select status from schedule where id = ?", Integer.class, scheduleId);
    }

    private int reloadedStatus(Long scheduleId) {
        return scheduleRepository.findById(scheduleId).orElseThrow().getStatus();
    }

    private void changeStatus(Schedule schedule, int status) {
        // 취소(9) 로 만드는 서비스 메서드가 없어 저장소로 직접 준비한다
        schedule.setStatus(status);
        scheduleRepository.save(schedule);
    }

    // ── create: 정상 ───────────────────────────────────────────────────

    @Test
    void 일정을_생성하면_예정_상태0으로_입력값_그대로_저장된다() {
        Schedule schedule = createTenToEleven();

        Schedule reloaded = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(0);
        assertThat(reloaded.getTitle()).isEqualTo("주간 회의");
        assertThat(reloaded.getOwnerId()).isEqualTo(owner.getId());
        assertThat(reloaded.getStartAt()).isEqualTo(TEN_O_CLOCK);
        assertThat(reloaded.getEndAt()).isEqualTo(ELEVEN_O_CLOCK);
        assertThat(reloaded.getLocation()).isEqualTo("3층 회의실");
        assertThat(storedStatus(schedule.getId())).isEqualTo(0);
    }

    @Test
    void 일정을_생성하면_생성_감사로그가_남는다(CapturedOutput output) {
        Schedule schedule = createTenToEleven();

        assertThat(output.getOut())
                .contains("[AUDIT] [")
                .contains("] SCHEDULE CREATE id=" + schedule.getId() + " by=" + owner.getId() + System.lineSeparator());
    }

    @Test
    void 소유자가_없는_사용자여도_생성된다() {
        // [보존 대상] 레거시는 소유자 존재 여부를 확인하지 않는다
        Schedule schedule = scheduleService.create("회의", MISSING_ID, TEN_O_CLOCK, ELEVEN_O_CLOCK, null);

        assertThat(schedule).isNotNull();
        assertThat(reloadedStatus(schedule.getId())).isEqualTo(0);
    }

    @Test
    void 시작과_끝이_같은_일정도_생성된다() {
        // [보존 대상] 끝이 시작보다 "앞"일 때만 거절 - 길이 0 일정은 통과
        Schedule schedule = scheduleService.create("회의", owner.getId(), TEN_O_CLOCK, TEN_O_CLOCK, null);

        assertThat(schedule).isNotNull();
    }

    @Test
    void 공백만_있는_제목도_생성된다() {
        // [보존 대상] null · 빈 문자열만 거절하고 공백 문자열은 통과
        Schedule schedule = scheduleService.create(" ", owner.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, null);

        assertThat(schedule).isNotNull();
    }

    // ── create: 입력 검증 실패 → null (예외 없음, 저장 · 로그 없음) ─────────────────

    @Test
    void 제목이_null이면_null을_돌려주고_저장하지_않는다(CapturedOutput output) {
        assertThat(scheduleService.create(null, owner.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, null)).isNull();

        assertThat(scheduleRepository.count()).isEqualTo(0);
        assertThat(output.getOut()).doesNotContain("SCHEDULE CREATE");
    }

    @Test
    void 제목이_빈_문자열이면_null을_돌려준다() {
        assertThat(scheduleService.create("", owner.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, null)).isNull();
        assertThat(scheduleRepository.count()).isEqualTo(0);
    }

    @Test
    void 시작_시각이_null이면_null을_돌려준다() {
        assertThat(scheduleService.create("회의", owner.getId(), null, ELEVEN_O_CLOCK, null)).isNull();
        assertThat(scheduleRepository.count()).isEqualTo(0);
    }

    @Test
    void 끝_시각이_null이면_null을_돌려준다() {
        assertThat(scheduleService.create("회의", owner.getId(), TEN_O_CLOCK, null, null)).isNull();
        assertThat(scheduleRepository.count()).isEqualTo(0);
    }

    @Test
    void 끝이_시작보다_앞이면_null을_돌려준다() {
        assertThat(scheduleService.create("회의", owner.getId(), ELEVEN_O_CLOCK, TEN_O_CLOCK, null)).isNull();
        assertThat(scheduleRepository.count()).isEqualTo(0);
    }

    // ── create: 시간 겹침 ───────────────────────────────────────────────

    @Test
    void 같은_소유자의_예정_일정과_겹치면_null을_돌려주고_저장하지_않는다(CapturedOutput output) {
        createTenToEleven();

        Schedule overlapping = scheduleService.create("다른 회의", owner.getId(),
                TEN_O_CLOCK.plusMinutes(30), NOON, null);

        assertThat(overlapping).isNull();
        assertThat(scheduleRepository.count()).isEqualTo(1);
        assertThat(output.getOut().split("SCHEDULE CREATE", -1).length - 1).isEqualTo(1);
    }

    @Test
    void 기존_일정을_완전히_감싸도_겹침으로_본다() {
        createTenToEleven();

        assertThat(scheduleService.create("긴 회의", owner.getId(),
                TEN_O_CLOCK.minusHours(1), NOON, null)).isNull();
    }

    @Test
    void 확정_상태1_일정과_겹쳐도_null이다() {
        Schedule existing = createTenToEleven();
        changeStatus(existing, 1);

        assertThat(scheduleService.create("다른 회의", owner.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, null)).isNull();
    }

    @Test
    void 기존_끝과_새_시작이_맞닿으면_겹치지_않아_생성된다() {
        createTenToEleven();

        Schedule adjacent = scheduleService.create("다음 회의", owner.getId(), ELEVEN_O_CLOCK, NOON, null);

        assertThat(adjacent).isNotNull();
        assertThat(scheduleRepository.count()).isEqualTo(2);
    }

    @Test
    void 새_끝과_기존_시작이_맞닿으면_겹치지_않아_생성된다() {
        scheduleService.create("점심 뒤 회의", owner.getId(), ELEVEN_O_CLOCK, NOON, null);

        assertThat(createTenToEleven()).isNotNull();
    }

    @Test
    void 취소_상태9_일정과는_겹쳐도_생성된다() {
        Schedule canceled = createTenToEleven();
        changeStatus(canceled, 9);

        assertThat(scheduleService.create("다른 회의", owner.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, null)).isNotNull();
    }

    @Test
    void 다른_사람의_일정과는_겹쳐도_생성된다() {
        createTenToEleven();

        assertThat(scheduleService.create("다른 회의", otherUser.getId(), TEN_O_CLOCK, ELEVEN_O_CLOCK, null)).isNotNull();
    }

    // ── confirm ───────────────────────────────────────────────────────

    @Test
    void 소유자가_예정_일정을_확정하면_상태1로_저장되고_감사로그가_남는다(CapturedOutput output) {
        Schedule schedule = createTenToEleven();

        scheduleService.confirm(schedule.getId(), owner.getId());

        assertThat(reloadedStatus(schedule.getId())).isEqualTo(1);
        assertThat(storedStatus(schedule.getId())).isEqualTo(1);
        assertThat(output.getOut())
                .contains("] SCHEDULE CONFIRM id=" + schedule.getId() + " by=" + owner.getId() + System.lineSeparator());
    }

    @Test
    void 소유자가_아닌_사용자가_확정하면_조용히_무시된다(CapturedOutput output) {
        Schedule schedule = createTenToEleven();

        assertThatCode(() -> scheduleService.confirm(schedule.getId(), otherUser.getId())).doesNotThrowAnyException();

        assertThat(reloadedStatus(schedule.getId())).isEqualTo(0);
        assertThat(output.getOut()).doesNotContain("SCHEDULE CONFIRM");
    }

    @Test
    void 이미_확정된_일정을_다시_확정하면_무시되고_로그도_한번만_남는다(CapturedOutput output) {
        Schedule schedule = createTenToEleven();
        scheduleService.confirm(schedule.getId(), owner.getId());

        scheduleService.confirm(schedule.getId(), owner.getId());

        assertThat(reloadedStatus(schedule.getId())).isEqualTo(1);
        assertThat(output.getOut().split("SCHEDULE CONFIRM", -1).length - 1).isEqualTo(1);
    }

    @Test
    void 취소_상태9_일정은_확정되지_않는다() {
        Schedule schedule = createTenToEleven();
        changeStatus(schedule, 9);

        scheduleService.confirm(schedule.getId(), owner.getId());

        assertThat(reloadedStatus(schedule.getId())).isEqualTo(9);
    }

    @Test
    void 없는_일정_id로_확정하면_예외없이_무시된다() {
        assertThatCode(() -> scheduleService.confirm(MISSING_ID, owner.getId())).doesNotThrowAnyException();
    }

    @Test
    void 없는_사용자가_확정하면_예외없이_무시된다() {
        Schedule schedule = createTenToEleven();

        assertThatCode(() -> scheduleService.confirm(schedule.getId(), MISSING_ID)).doesNotThrowAnyException();

        assertThat(reloadedStatus(schedule.getId())).isEqualTo(0);
    }

    @Test
    void 소유자가_null인_일정을_확정하면_NullPointerException이_난다() {
        // [보존 대상 · 결함 후보] sc.getOwnerId().equals(userId) 가 null 을 확인하지 않는다 (재설계 단계에서 다룸)
        Schedule ownerless = scheduleService.create("회의", null, TEN_O_CLOCK, ELEVEN_O_CLOCK, null);

        assertThatThrownBy(() -> scheduleService.confirm(ownerless.getId(), owner.getId()))
                .isInstanceOf(NullPointerException.class);
    }
}
