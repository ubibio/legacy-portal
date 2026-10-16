package kr.hull.portal.approval;

import kr.hull.portal.approval.dto.ApprovalResponse;
import kr.hull.portal.approval.dto.CreateApprovalRequest;
import kr.hull.portal.approval.dto.ProcessRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 결재 REST 컨트롤러.
 * [스멜] 컨트롤러가 Map 으로 파라미터를 받아 그대로 흘려보낸다(요청 DTO 부재, 검증 없음).
 * [스멜] 서비스의 매직넘버 action 을 그대로 노출한다.
 *
 * [리팩토링] 레거시 Map 요청 · Entity 응답 → 요청 DTO(CreateApprovalRequest · ProcessRequest) ·
 *   응답 DTO(ApprovalResponse) record 로 분리, approval.dto 패키지 (기법: DTO 도입 · 스멜 S12 · 백로그 R11)
 *   - 기본값(amount 0 · urgent false · reason "")은 DTO 의 compact 생성자가 채운다
 *   - 보존: URL · HTTP 메서드, 요청/응답 JSON 필드 이름 · 순서 · 정수 code, 필드 생략 시 기본값, process 의 200 + 빈 본문
 *   - 보존: 서비스 public 시그니처(create 8개 인자 · processApproval 4개 인자)는 그대로 - 컨트롤러가 DTO 를 풀어서 넘긴다
 *   - 남은 것: action 은 API 계약이라 정수 그대로 받는다(서버 내부에서만 의미가 있는 값). 입력 검증은 재설계 단계
 *   - [이름 개선] service → approvalService
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping
    public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
        Approval approval = approvalService.create(
                request.title(),
                request.content(),
                request.type(),        // [보존] 생략 시 null 언박싱 → NPE(500), 레거시 (int) 형변환과 같은 결과
                request.priority(),
                request.drafterId().longValue(),    // [보존] 생략 시 NPE(500), 레거시 ((Number) null).longValue() 와 같은 결과
                request.approverId().longValue(),
                request.amount(),     // compact 생성자가 생략 시 0 으로 채움
                request.urgent());    // compact 생성자가 생략 시 false 로 채움
        return ApprovalResponse.from(approval);
    }

    // action: 1=상신, 2=승인, 3=반려, 9=취소  ([보존] API 계약 - 정수 그대로)
    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody ProcessRequest request) {
        approvalService.processApproval(
                id,
                request.userId().longValue(),   // [보존] 생략 시 NPE(500)
                request.action(),
                request.reason());              // compact 생성자가 생략 시 "" 로 채움
    }

    @GetMapping("/drafts/{userId}")
    public List<ApprovalResponse> drafts(@PathVariable Long userId) {
        return approvalService.myDrafts(userId).stream().map(ApprovalResponse::from).toList();
    }

    @GetMapping("/inbox/{userId}")
    public List<ApprovalResponse> inbox(@PathVariable Long userId) {
        return approvalService.myInbox(userId).stream().map(ApprovalResponse::from).toList();
    }
}
