package kr.hull.portal.schedule;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 일정 엔티티. [스멜] 빈약한 도메인 모델 + 원시 타입.
 * status: 0=예정, 1=확정, 9=취소
 *
 * [리팩토링] 빈약한 도메인 → 풍부한 도메인 (기법: 기능 욕심 해소 · 로직을 데이터가 있는 곳으로 이동 · Tell, Don't Ask · 결재 R09 와 같은 방식)
 *   - 레거시: ScheduleService 가 getStatus() · getStartAt() · getEndAt() · getOwnerId() 를 꺼내
 *     입력 검증 · 시간 겹침 · 확정 조건을 39줄 한 덩어리로 계산했다
 *   - 여기서: isCreatable(입력 검증) · draft(생성 규칙) · overlaps(겹침 판정) · confirm(확정 조건 + 상태 변경)을 일정이 스스로 판단한다
 * [보존] DB 컬럼 · 저장값(status 0/1/9) 그대로, public getter/setter 그대로(특성화 테스트가 setStatus(1 · 9) 로 준비한다),
 *   겹침 경계(끝과 시작이 맞닿으면 안 겹침), 검증 순서, 소유자 null 이면 확정 때 NullPointerException(레거시 결함 - 재설계 대상)
 * [남은 것] status 는 아직 int + 이름 붙인 상수 - ScheduleStatus enum 은 R07 단계
 */
@Entity
public class Schedule {

    // [리팩토링] 매직넘버에 이름을 붙임 - 일정 전용 의미 (결재 · 공지의 9 와 숫자만 같다 = 우연 중복이라 공유하지 않는다)
    private static final int PLANNED_STATUS = 0;     // 예정 (확정 전)
    private static final int CONFIRMED_STATUS = 1;   // 확정
    private static final int CANCELED_STATUS = 9;    // 취소

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private int status;     // 0=예정 1=확정 9=취소 (숫자만 저장 → 의미는 주석에만)
    private Long ownerId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String location;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    // ── [리팩토링] 생성 규칙 ─────────────────────────────────────────────

    /**
     * 이 입력으로 일정을 만들 수 있는가 (입력 검증).
     * [리팩토링] 레거시 ScheduleService.create 앞부분의 if 3개를 이름 있는 규칙으로 (기법: 조건문 분해 + 함수 옮기기).
     * [보존] 검사 순서 · 기준 그대로 - 제목 null 또는 "" 거절(공백 " " 은 통과), 시각 null 거절,
     *   끝이 시작보다 "앞"일 때만 거절(시작 = 끝 인 길이 0 일정은 통과).
     */
    public static boolean isCreatable(String title, LocalDateTime startAt, LocalDateTime endAt) {
        if (title == null || title.equals("")) {
            return false;
        }
        if (startAt == null || endAt == null) {
            return false;
        }
        return !endAt.isBefore(startAt);
    }

    /**
     * 새 일정을 예정(0) 상태로 만든다.
     * [리팩토링] 레거시: 서비스가 setter 를 줄줄이 부르며 초기 상태 0 을 직접 정함 → 엔티티의 정적 팩토리로.
     * [보존] 입력값 그대로 저장(소유자 존재 확인 없음 - 레거시 동작), 상태 0.
     */
    public static Schedule draft(String title, Long ownerId, LocalDateTime startAt, LocalDateTime endAt, String location) {
        Schedule schedule = new Schedule();
        schedule.title = title;
        schedule.ownerId = ownerId;
        schedule.startAt = startAt;
        schedule.endAt = endAt;
        schedule.location = location;
        schedule.status = PLANNED_STATUS;
        return schedule;
    }

    // ── [리팩토링] 도메인 행위 ───────────────────────────────────────────

    /**
     * 이 일정이 주어진 시간대와 겹치는가.
     * [리팩토링] 레거시: 서비스 루프 안 if (status != 9) { if (기존 시작 < 새 끝 && 새 시작 < 기존 끝) flag1 = true } →
     *   겹침 판정을 일정 자신에게 묻는다(Tell, Don't Ask).
     * [보존] 취소(9) 일정은 겹치지 않는 것으로 본다. 경계는 "미만"(<) - 끝과 시작이 맞닿기만 하면 겹치지 않음.
     */
    public boolean overlaps(LocalDateTime otherStartAt, LocalDateTime otherEndAt) {
        if (status == CANCELED_STATUS) {
            return false;
        }
        return startAt.isBefore(otherEndAt) && otherStartAt.isBefore(endAt);
    }

    /**
     * 확정. 소유자 본인이, 예정(0) 상태일 때만 된다.
     * [보존] 레거시와 같은 순서로 검사 - 소유자 비교를 먼저 하므로 소유자가 null 이면 NullPointerException
     *   (레거시 sc.getOwnerId().equals(userId) 결함을 그대로 둔다. 재설계 단계에서 다룸).
     * @return 확정됐으면 true, 조건이 안 맞아 아무것도 바꾸지 않았으면 false
     */
    public boolean confirm(Long requesterId) {
        if (!ownerId.equals(requesterId)) {
            return false;
        }
        if (status != PLANNED_STATUS) {
            return false;
        }
        status = CONFIRMED_STATUS;
        return true;
    }
}
