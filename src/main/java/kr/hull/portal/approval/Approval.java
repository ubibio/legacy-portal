package kr.hull.portal.approval;

import jakarta.persistence.*;
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
 */
@Entity
public class Approval {

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
}
