package kr.hull.portal.notice;

import jakarta.persistence.*;
import kr.hull.portal.user.User;

import java.time.LocalDateTime;

/**
 * 공지 엔티티. [스멜] 빈약한 도메인 모델 + 원시 타입(status int).
 * status: 0=임시, 1=게시, 9=내림
 *
 * [리팩토링] 빈약한 도메인 → 풍부한 도메인 (기법: 기능 욕심 해소 · 로직을 데이터가 있는 곳으로 이동 · Tell, Don't Ask · 결재 R09 와 같은 방식)
 *   - 레거시: NoticeService 가 getStatus() · getCategory() · user.getRole() 을 꺼내 게시 조건 · 긴급 여부 · 라벨을 대신 계산했다
 *   - 여기서: draft(생성 규칙) · publish(게시 조건 + 상태 변경) · isEmergency · statusLabel 을 공지가 스스로 판단한다
 *     publish 는 조건이 안 맞으면 아무것도 바꾸지 않고 false → 서비스는 false 면 조용히 끝낸다(레거시 "조용히 무시" 보존)
 * [보존] DB 컬럼 · 저장값(status 0/1/9, category 1/2/3) 그대로, public getter/setter 그대로(특성화 테스트가 setStatus(9) 로 준비한다)
 * [남은 것] status · category 는 아직 int + 이름 붙인 상수 - NoticeStatus · NoticeCategory enum 은 R07 단계
 */
@Entity
public class Notice {

    // [리팩토링] 매직넘버에 이름을 붙임 - 공지 전용 의미다(결재의 0 · 9 와 숫자만 같고 뜻이 다르다 = 우연 중복이라 공유하지 않는다)
    private static final int DRAFT_STATUS = 0;        // 임시 (게시 전)
    private static final int PUBLISHED_STATUS = 1;    // 게시
    private static final int TAKEN_DOWN_STATUS = 9;   // 내림
    private static final int EMERGENCY_CATEGORY = 2;  // 분류 2 = 긴급 (1 일반 · 3 인사)

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    private int status;     // 0=임시 1=게시 9=내림 (숫자만 저장 → 의미는 주석에만)
    private int category;   // 1=일반 2=긴급 3=인사 (enum 후보)
    private Long writerId;
    private boolean pinned;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public int getCategory() { return category; }
    public void setCategory(int category) { this.category = category; }
    public Long getWriterId() { return writerId; }
    public void setWriterId(Long writerId) { this.writerId = writerId; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // ── [리팩토링] 생성 규칙 ─────────────────────────────────────────────

    /**
     * 새 공지를 임시(0) 상태로 만든다.
     * [리팩토링] 레거시: NoticeService.create 가 setter 를 줄줄이 부르며 초기 상태 0 을 직접 정함 → 엔티티의 정적 팩토리로.
     * [보존] 입력값 그대로 저장(작성자 존재 · 권한 확인 없음 - 레거시 동작), 상태 0, 생성 시각 = 지금.
     */
    public static Notice draft(String title, String content, int category, Long writerId, boolean pinned,
                               LocalDateTime now) {
        Notice notice = new Notice();
        notice.title = title;
        notice.content = content;
        notice.category = category;
        notice.status = DRAFT_STATUS;
        notice.writerId = writerId;
        notice.pinned = pinned;
        notice.createdAt = now;
        return notice;
    }

    // ── [리팩토링] 도메인 행위 ───────────────────────────────────────────

    /**
     * 게시. 게시 권한(팀장 이상)이 있는 사용자가, 임시(0) 상태일 때만 된다.
     * [보존] 레거시 if (role >= 2) { if (status == 0) { status = 1 } } 와 같은 결과. 내림(9) · 이미 게시(1)는 무시.
     * @return 게시됐으면 true, 조건이 안 맞아 아무것도 바꾸지 않았으면 false
     */
    public boolean publish(User publisher) {
        if (!publisher.hasNoticePublishAuthority()) {
            return false;
        }
        if (status != DRAFT_STATUS) {
            return false;
        }
        status = PUBLISHED_STATUS;
        return true;
    }

    /** 긴급 공지인가 - 게시 때 전 직원 메일 대상. [보존] 분류 2 만 긴급(일반 1 · 인사 3 은 메일 없음). */
    public boolean isEmergency() {
        return category == EMERGENCY_CATEGORY;
    }

    /**
     * 상태 표시 라벨.
     * [리팩토링] Move Method - 레거시 NoticeService.statusLabel 의 if 사다리를 데이터가 있는 엔티티로 옮김.
     * [보존] 0 임시 · 1 게시 · 9 내림 · 그 밖 "알수없음". get 접두가 없어 JSON 필드가 생기지 않는다.
     */
    public String statusLabel() {
        if (status == DRAFT_STATUS) return "임시";
        if (status == PUBLISHED_STATUS) return "게시";
        if (status == TAKEN_DOWN_STATUS) return "내림";
        return "알수없음";
    }
}
