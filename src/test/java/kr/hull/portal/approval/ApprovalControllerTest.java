package kr.hull.portal.approval;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [특성화 테스트 - API 계약] 결재 · 사용자 API 의 현재 요청/응답 JSON 형식을 고정한다.
 *
 * - 리팩토링(DTO 분리 R11 등) 후에도 URL · HTTP 메서드 · JSON 필드 이름 · 숫자 code 가 그대로인지 지키는 안전망.
 * - 기대값은 앱을 띄워 실제 응답을 관찰한 값이다 (결재 JSON 필드 12개, status/type/priority 는 정수,
 *   amount 생략 시 0, urgent 면 priority 3, process 는 200 + 빈 본문, 없는 id 도 200).
 * - 사용자 id 1~4 는 DataSeeder 초기 데이터 (1 김사원 · 2 박팀장 · 3 이임원 · 4 최사원).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApprovalControllerTest {

    private static final String CREATE_REQUEST = """
            {"title":"노트북 구매","content":"개발용 노트북","type":1,"priority":2,
             "drafterId":1,"approverId":2,"amount":1200000,"urgent":false}""";

    @Autowired
    private MockMvc mockMvc;

    private long createApproval(String requestJson) throws Exception {
        String responseJson = mockMvc.perform(post("/api/approvals")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(responseJson, "$.id")).longValue();
    }

    @Test
    void 결재_생성_응답은_필드_12개의_레거시_JSON이다() throws Exception {
        mockMvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(12)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("노트북 구매"))
                .andExpect(jsonPath("$.content").value("개발용 노트북"))
                .andExpect(jsonPath("$.type").value(1))
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.priority").value(2))
                .andExpect(jsonPath("$.drafterId").value(1))
                .andExpect(jsonPath("$.approverId").value(2))
                .andExpect(jsonPath("$.rejectReason").value(nullValue()))
                .andExpect(jsonPath("$.amount").value(1200000))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());
    }

    @Test
    void 금액과_긴급을_생략하면_금액_0_우선순위는_요청값으로_생성된다() throws Exception {
        String requestWithoutAmount = """
                {"title":"휴가","content":"연차","type":2,"priority":1,"drafterId":1,"approverId":2}""";

        mockMvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON).content(requestWithoutAmount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0))
                .andExpect(jsonPath("$.priority").value(1))
                .andExpect(jsonPath("$.type").value(2));
    }

    @Test
    void 긴급으로_생성하면_응답의_우선순위가_3이다() throws Exception {
        String urgentRequest = """
                {"title":"장애 대응","content":"긴급","type":1,"priority":1,
                 "drafterId":1,"approverId":2,"amount":5000,"urgent":true}""";

        mockMvc.perform(post("/api/approvals").contentType(MediaType.APPLICATION_JSON).content(urgentRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(3));
    }

    @Test
    void 처리_API는_200과_빈_본문을_돌려주고_상신이_반영된다() throws Exception {
        long approvalId = createApproval(CREATE_REQUEST);

        mockMvc.perform(post("/api/approvals/" + approvalId + "/process")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1,\"action\":1}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/approvals/drafts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].status").value(1))
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].priority").value(3));
    }

    @Test
    void 반려_요청에_reason을_빼면_반려사유가_빈_문자열로_저장된다() throws Exception {
        long approvalId = createApproval(CREATE_REQUEST);
        mockMvc.perform(post("/api/approvals/" + approvalId + "/process")
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1,\"action\":1}"));

        mockMvc.perform(post("/api/approvals/" + approvalId + "/process")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":2,\"action\":3}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/approvals/inbox/2"))
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].status").value(3))
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].rejectReason").value(""));
    }

    @Test
    void 없는_결재_id를_처리해도_200과_빈_본문이다() throws Exception {
        mockMvc.perform(post("/api/approvals/999999/process")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":2,\"action\":2}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void 받은_결재함은_결재자_기준이고_없으면_빈_배열이다() throws Exception {
        long approvalId = createApproval(CREATE_REQUEST);

        mockMvc.perform(get("/api/approvals/inbox/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + approvalId + ")].approverId").value(2));
        mockMvc.perform(get("/api/approvals/inbox/4"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void 사용자_목록은_초기_사용자_4명을_5개_필드로_돌려준다() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[1].*", hasSize(5)))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("박팀장"))
                .andExpect(jsonPath("$[1].email").value("park@hull.kr"))
                .andExpect(jsonPath("$[1].role").value(2))
                .andExpect(jsonPath("$[1].dept").value("개발1팀"));
    }
}
