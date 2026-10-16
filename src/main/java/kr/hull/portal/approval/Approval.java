package kr.hull.portal.approval;

import jakarta.persistence.*;
import kr.hull.portal.user.User;

import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 빈약한 도메인 모델(Anemic Domain Model) — 데이터만 있고 행위가 없다.
 * [스멜] 원시 타입 집착(Primitive Obsession) — status, type, priority 가 모두 int.
 *        status: 0=임시저장, 1=상신, 2=승인, 3=반려, 9=취소  (의미가 코드 곳곳에 흩어짐)
 *        type:   1=지출, 2=휴가, 3=구매, 4=기타
 *        priority: 1=낮음, 2=보통, 3=높음
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter. 누구나 상태를 마음대로 바꿀 수 있다.
 *
 * [리팩토링] status 필드: 레거시 int → ApprovalStatus enum (기법: 매직넘버를 enum 으로 · @Convert)
 *   - 보존: DB 컬럼 값(0/1/2/3/9, NOT NULL), getStatus() 는 int code 반환(응답 JSON "status": 1 그대로 · 특성화 테스트 계약),
 *           setStatus(int) 시그니처, 새 객체의 기본 상태 0(레거시 int 기본값 = 임시저장).
 *   - 추가: approvalStatus() / changeStatus(ApprovalStatus) - 프로덕션 코드는 이것으로 enum 비교 · 변경.
 *           이름에 get/set 접두를 붙이지 않아 JSON 에 새 필드가 생기지 않는다.
 *   - 남은 것: type · priority 는 아직 int (백로그 R07).
 *
 * [리팩토링] 빈약한 도메인 → 풍부한 도메인 (기법: 기능 욕심 해소 · 로직을 데이터가 있는 곳으로 이동 · 백로그 R09)
 *   - 레거시: ApprovalService 가 getStatus() · getApproverId() · getAmount() 를 꺼내 상태 전이 · 권한 · 금액 규칙을 대신 계산
 *   - 여기서: submit / approve / reject / cancel 이 전제조건 검사 + 상태 · 수정시각 변경을 스스로 한다.
 *            조건이 안 맞으면 아무것도 바꾸지 않고 false 를 돌려준다 → 서비스는 false 면 조용히 끝낸다(레거시 "조용히 무시" 보존)
 *   - 금액 등급(amountGrade) · 결재자/기안자 본인 판정도 엔티티로.
 *   - JSON 보존: 새 메서드는 get 접두가 없거나(amountGrade) 인자가 있어서(isApprover(Long) 등) 응답 필드로 나가지 않는다 - 필드 12개 그대로
 *   - 남은 것: public setter 는 레거시 계약이라 그대로 둔다 (캡슐화는 재설계 단계)
 */
@Entity
public class Approval {

    private static final int EXPENSE_TYPE = 1;                            // 유형 1 = 지출
    private static final int HIGH_PRIORITY = 3;                           // 우선순위 3 = 높음
    private static final long HIGH_PRIORITY_EXPENSE_THRESHOLD = 1_000_000L; // 상신 시 우선순위 상향 기준(지출 100만원 이상)
    // [리팩토링] 금액 등급 경계 상수(GRADE_S/A/B_MINIMUM_AMOUNT)는 AmountGrade enum 으로 이동 (Move Method).
    //   위 상향 기준과 값(100만원)이 같아도 다른 규칙이라 섞지 않는다

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    private int type;       // 1=지출 2=휴가 3=구매 4=기타 (의미를 주석으로만 설명 → enum 후보)
    // [리팩토링] int → ApprovalStatus. DB 에는 converter 가 레거시 정수 code 로 저장.
    //   NOT NULL 은 레거시 int 컬럼과 같게, 기본값 DRAFT 는 레거시 int 기본값 0 과 같게 맞춘다.
    @Convert(converter = ApprovalStatusConverter.class)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.DRAFT;
    private int priority;   // 1=낮음 2=보통 3=높음
    private Long drafterId;     // 기안자
    private Long approverId;    // 결재자
    private String rejectReason;
    private long amount;        // 지출/구매 금액
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    // [보존] 레거시 계약 - int code 로 읽고 쓴다 (응답 JSON · 특성화 테스트가 이 값을 본다)
    public int getStatus() { return status.getCode(); }
    public void setStatus(int status) { this.status = ApprovalStatus.fromCode(status); }
    // [리팩토링] 프로덕션 코드용 enum 접근 - get/set 접두가 없어 JSON 직렬화 대상이 아니다
    public ApprovalStatus approvalStatus() { return status; }
    public void changeStatus(ApprovalStatus status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Long getDrafterId() { return drafterId; }
    public void setDrafterId(Long drafterId) { this.drafterId = drafterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ── [리팩토링 R10] 생성 규칙 ─────────────────────────────────────────────

    /**
     * 새 결재를 임시저장 상태로 만든다.
     * [리팩토링] 레거시: ApprovalService.create 가 setter 를 줄줄이 부르며 초기값 규칙(긴급이면 우선순위 3, 상태 임시저장)을 직접 정함
     *   → 생성 규칙을 엔티티의 정적 팩토리로 (기법: 생성자 대신 정적 팩토리 · 이유: 생성 규칙 변경은 결재 흐름과 다른 변경 이유(SRP)).
     * [보존] 긴급이면 요청 우선순위와 상관없이 3, 상태 임시저장(DB 0), 생성 · 수정 시각 = 지금.
     *   (레거시는 생성 · 수정 시각에 now() 를 두 번 불렀지만 같은 순간이라 한 값으로 맞춘다)
     */
    public static Approval draft(String title, String content, int type, int priority,
                                 Long drafterId, Long approverId, long amount, boolean urgent, LocalDateTime now) {
        Approval approval = new Approval();
        approval.title = title;
        approval.content = content;
        approval.type = type;
        approval.priority = urgent ? HIGH_PRIORITY : priority;
        approval.status = ApprovalStatus.DRAFT;
        approval.drafterId = drafterId;
        approval.approverId = approverId;
        approval.amount = amount;
        approval.createdAt = now;
        approval.updatedAt = now;
        return approval;
    }

    // ── [리팩토링 R09] 도메인 행위 ───────────────────────────────────────────

    /**
     * 상신. 임시저장일 때만 된다. 지출 · 100만원 이상이면 우선순위를 높음(3)으로 올린다.
     * [보존] 요청자가 기안자인지는 보지 않는다(레거시 동작 - 재설계 단계 결함 후보).
     * @return 상신됐으면 true, 조건이 안 맞아 아무것도 바꾸지 않았으면 false
     */
    public boolean submit(LocalDateTime now) {
        if (status != ApprovalStatus.DRAFT) {
            return false;
        }
        if (type == EXPENSE_TYPE && amount >= HIGH_PRIORITY_EXPENSE_THRESHOLD) {
            priority = HIGH_PRIORITY;
        }
        status = ApprovalStatus.SUBMITTED;
        updatedAt = now;
        return true;
    }

    /** 승인. 상신 상태 + 요청자가 지정된 결재자 + 결재 권한(팀장 이상)일 때만. */
    public boolean approve(User requester, LocalDateTime now) {
        if (!canBeDecidedBy(requester)) {
            return false;
        }
        status = ApprovalStatus.APPROVED;
        updatedAt = now;
        return true;
    }

    /** 반려. 승인과 같은 조건. [보존] 사유는 검증 없이 그대로 저장(빈 문자열 · null 도). */
    public boolean reject(User requester, String reason, LocalDateTime now) {
        if (!canBeDecidedBy(requester)) {
            return false;
        }
        status = ApprovalStatus.REJECTED;
        rejectReason = reason;
        updatedAt = now;
        return true;
    }

    /** 취소. 승인 전(임시저장 · 상신) + 요청자가 기안자 본인일 때만. [보존] 역할(role)은 보지 않는다. */
    public boolean cancel(Long requesterId, LocalDateTime now) {
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (!isDraftedBy(requesterId)) {
            return false;
        }
        status = ApprovalStatus.CANCELED;
        updatedAt = now;
        return true;
    }

    /** 지정된 결재자 본인인가. [보존] 결재자 미지정(null)이면 false. */
    public boolean isApprover(Long userId) {
        return approverId != null && approverId.equals(userId);
    }

    /** 기안자 본인인가. [보존] 기안자 미지정(null)이면 false. */
    public boolean isDraftedBy(Long userId) {
        return drafterId != null && drafterId.equals(userId);
    }

    /**
     * 이 결재의 금액 등급.
     * [리팩토링] Move Method - 등급 계산(if 사다리 · 경계 상수)은 AmountGrade.of 로 옮기고 여기서는 위임만 한다.
     *   반환 타입도 String → AmountGrade (등급이 문자열이 아니라 도메인 값이 됨). get 접두가 없어 JSON 에 나가지 않는다.
     */
    public AmountGrade amountGrade() {
        return AmountGrade.of(amount);
    }

    // 승인 · 반려 공통 전제조건 - 레거시는 이 3단 검사가 approve · reject 에 복붙돼 있었다(스멜 S08 · 02 문서 D3 · D8)
    private boolean canBeDecidedBy(User requester) {
        return status == ApprovalStatus.SUBMITTED
                && isApprover(requester.getId())
                && requester.hasApprovalAuthority();
    }
}
