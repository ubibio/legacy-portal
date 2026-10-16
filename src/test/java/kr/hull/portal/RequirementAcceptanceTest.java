package kr.hull.portal;

import com.jayway.jsonpath.JsonPath;
import kr.hull.portal.approval.ApprovalStatus;
import kr.hull.portal.notice.Notice;
import kr.hull.portal.notice.NoticeService;
import kr.hull.portal.schedule.Schedule;
import kr.hull.portal.schedule.ScheduleService;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [요구사항 인수 테스트] 요구사항정의서 v3.1.3 의 「검증 기준」 중 기존 특성화 테스트가 덮지 않던 항목을 채운다.
 *
 * - 대응표: docs/요구사항_TASK/검증표.md (요구사항 ID · 검증 기준 → 테스트 메서드)
 * - 기존 특성화 테스트(*CharacterizationTest · ApprovalControllerTest 등)는 고치지 않고 이 파일에만 더한다.
 * - 현재 동작을 고정할 뿐 바꾸지 않는다. 사용자 id 1~4 는 DataSeeder 초기 데이터 (1 김사원 · 2 박팀장 · 3 이임원 · 4 최사원).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RequirementAcceptanceTest {

    private static final long DRAFTER_ID = 1L;        // 김사원
    private static final long TEAM_LEADER_ID = 2L;    // 박팀장 - 결재자
    private static final long STAFF_ID = 4L;          // 최사원
    private static final int EXPENSE_TYPE = 1;
    private static final int SUBMIT_ACTION = 1;
    private static final int APPROVE_ACTION = 2;
    private static final int REJECT_ACTION = 3;
    private static final int EMERGENCY_CATEGORY = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSeeder dataSeeder;

    @Autowired
    private Clock clock;

    private long createApproval(String title, long amount) throws Exception {
        String requestJson = """
                {"title":"%s","content":"내용","type":%d,"priority":2,
                 "drafterId":%d,"approverId":%d,"amount":%d}""".formatted(title, EXPENSE_TYPE, DRAFTER_ID, TEAM_LEADER_ID, amount);
        String responseJson = mockMvc.perform(post("/api/approvals")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(responseJson, "$.id")).longValue();
    }

    private void process(long approvalId, long userId, int action, String reason) throws Exception {
        String requestJson = """
                {"userId":%d,"action":%d,"reason":"%s"}""".formatted(userId, action, reason);
        mockMvc.perform(post("/api/approvals/" + approvalId + "/process")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isOk());
    }

    // ── REQ-F-001 결재 기안 ─────────────────────────────────────────────────

    @Test
    void 기안_생성_응답의_상태는_0이고_생성시각과_수정시각이_같다() throws Exception {
        String responseJson = mockMvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"노트북 구매","content":"개발용","type":1,"priority":2,"drafterId":1,"approverId":2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.amount").value(0))
                .andReturn().getResponse().getContentAsString();

        String createdAt = JsonPath.read(responseJson, "$.createdAt");
        String updatedAt = JsonPath.read(responseJson, "$.updatedAt");
        assertThat(createdAt).isEqualTo(updatedAt);
    }

    // ── REQ-F-002 상신 ──────────────────────────────────────────────────────

    @Test
    void 이미_상신된_문서를_다시_상신하면_메일도_감사로그도_추가되지_않는다(CapturedOutput output) throws Exception {
        long approvalId = createApproval("모니터 구매", 300_000L);
        process(approvalId, DRAFTER_ID, SUBMIT_ACTION, "");
        int outputLengthAfterFirstSubmit = output.getOut().length();

        process(approvalId, DRAFTER_ID, SUBMIT_ACTION, "");

        assertThat(output.getOut().substring(outputLengthAfterFirstSubmit))
                .doesNotContain("=== MAIL ===")
                .doesNotContain("APPROVAL SUBMIT");
        mockMvc.perform(get("/api/approvals/drafts/" + DRAFTER_ID))
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].status").value(1));
    }

    @Test
    void 상신의_부수효과는_메일_다음에_감사로그_순서다(CapturedOutput output) throws Exception {
        long approvalId = createApproval("키보드 구매", 50_000L);
        int outputLengthAfterCreate = output.getOut().length();

        process(approvalId, DRAFTER_ID, SUBMIT_ACTION, "");

        String printed = output.getOut().substring(outputLengthAfterCreate);
        assertThat(printed.indexOf("SUBJECT: [결재요청] 키보드 구매")).isNotNegative();
        assertThat(printed.indexOf("APPROVAL SUBMIT id=" + approvalId))
                .isGreaterThan(printed.indexOf("SUBJECT: [결재요청] 키보드 구매"));
    }

    // ── REQ-F-004 반려 ──────────────────────────────────────────────────────

    @Test
    void 정상_반려_후_조회_응답의_반려사유가_입력한_사유와_같다() throws Exception {
        long approvalId = createApproval("출장비", 200_000L);
        process(approvalId, DRAFTER_ID, SUBMIT_ACTION, "");

        process(approvalId, TEAM_LEADER_ID, REJECT_ACTION, "증빙 누락");

        mockMvc.perform(get("/api/approvals/drafts/" + DRAFTER_ID))
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].status").value(3))
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].rejectReason").value("증빙 누락"));
    }

    // ── REQ-F-006 내 기안함 · 내 결재함 ───────────────────────────────────────

    @Test
    void 기안자가_만든_문서_2건이_내_기안함에_모두_12개_필드로_나온다() throws Exception {
        long firstApprovalId = createApproval("첫 번째", 10_000L);
        long secondApprovalId = createApproval("두 번째", 20_000L);

        mockMvc.perform(get("/api/approvals/drafts/" + DRAFTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + firstApprovalId + ")].title").value("첫 번째"))
                .andExpect(jsonPath("$[?(@.id == " + secondApprovalId + ")].title").value("두 번째"))
                .andExpect(jsonPath("$[0].*", hasSize(12)));
    }

    @Test
    void 결재자에게_온_문서는_승인_반려_후에도_내_결재함에_남는다() throws Exception {
        long approvedId = createApproval("승인될 문서", 10_000L);
        long rejectedId = createApproval("반려될 문서", 10_000L);
        process(approvedId, DRAFTER_ID, SUBMIT_ACTION, "");
        process(rejectedId, DRAFTER_ID, SUBMIT_ACTION, "");

        process(approvedId, TEAM_LEADER_ID, APPROVE_ACTION, "");
        process(rejectedId, TEAM_LEADER_ID, REJECT_ACTION, "보류");

        mockMvc.perform(get("/api/approvals/inbox/" + TEAM_LEADER_ID))
                .andExpect(jsonPath("$[?(@.id == " + approvedId + ")].status").value(2))
                .andExpect(jsonPath("$[?(@.id == " + rejectedId + ")].status").value(3));
    }

    // ── REQ-F-009 공지 작성 ─────────────────────────────────────────────────

    @Test
    void 작성한_공지의_상태_표시명은_임시다() {
        Notice notice = noticeService.create("점검 안내", "토요일 점검", EMERGENCY_CATEGORY, TEAM_LEADER_ID, false);

        assertThat(noticeService.statusLabel(notice)).isEqualTo("임시");
    }

    // ── REQ-F-011 일정 시간 중복 ─────────────────────────────────────────────

    @Test
    void 열시_열한시_일정이_있으면_열한시_시작은_등록되고_열시반_시작은_거부된다() {
        LocalDateTime tenOclock = LocalDateTime.of(2026, 10, 19, 10, 0);
        scheduleService.create("주간 회의", STAFF_ID, tenOclock, tenOclock.plusHours(1), "회의실 A");

        Schedule adjacentSchedule = scheduleService.create("고객 미팅", STAFF_ID,
                tenOclock.plusHours(1), tenOclock.plusHours(2), "회의실 B");
        Schedule overlappingSchedule = scheduleService.create("면담", STAFF_ID,
                tenOclock.plusMinutes(30), tenOclock.plusMinutes(90), "회의실 C");

        assertThat(adjacentSchedule).isNotNull();
        assertThat(overlappingSchedule).isNull();
    }

    // ── REQ-F-013 초기 사용자 ───────────────────────────────────────────────

    @Test
    void 초기_데이터를_다시_넣어도_사용자_수가_늘지_않는다() {
        long userCountBefore = userRepository.count();

        dataSeeder.run();

        assertThat(userCountBefore).isEqualTo(4);
        assertThat(userRepository.count()).isEqualTo(4);
    }

    // ── REQ-IF-001 메일 인터페이스 ───────────────────────────────────────────

    @Test
    void 받는_사람이_DB에_없으면_메일을_보내지_않는다(CapturedOutput output) throws Exception {
        String responseJson = mockMvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"없는 결재자","content":"내용","type":2,"priority":1,"drafterId":1,"approverId":999}"""))
                .andReturn().getResponse().getContentAsString();
        long approvalId = ((Number) JsonPath.read(responseJson, "$.id")).longValue();
        int outputLengthAfterCreate = output.getOut().length();

        process(approvalId, DRAFTER_ID, SUBMIT_ACTION, "");

        assertThat(output.getOut().substring(outputLengthAfterCreate))
                .doesNotContain("=== MAIL ===")
                .contains("APPROVAL SUBMIT id=" + approvalId);
    }

    // ── REQ-DA-001 코드값 ───────────────────────────────────────────────────

    @Test
    void 정의되지_않은_결재_상태_코드를_읽으면_예외가_난다() {
        assertThatThrownBy(() -> ApprovalStatus.fromCode(4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── REQ-NF-004 시각 주입 ────────────────────────────────────────────────

    @Test
    void 앱의_Clock_빈은_시스템_기본_시간대다() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }
}
