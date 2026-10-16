package kr.hull.portal.schedule;

import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 일정 서비스.
 * [스멜4] 감사 로그 코드가 또 복붙. [스멜] 시간 겹침(중복 예약) 검증이 길고 읽기 어렵다.
 *
 * [리팩토링 R02] 레거시: new FileAuditLogger() 직접 생성
 *   → AuditLogger 인터페이스를 생성자로 주입받는다 (기법: 생성자 주입 · 이유: DIP - 출력 대상을 바꿔도 이 클래스는 그대로)
 * [리팩토링] 감사로그 시각 포맷 · 줄 조립 복붙 2곳 → auditLogger.write(동작, id, 행위자) 위임 (형식은 ConsoleAuditLogger 한 곳)
 * [리팩토링] 검증 · 겹침 · 확정 규칙을 엔티티로 이동 (기법: Tell, Don't Ask · 조건문 분해 · 결재 R09 와 같은 방식)
 *   - 레거시 create 39줄: 입력 검증 if 3개 + flag1 루프(getStatus · getStartAt · getEndAt 를 꺼내 비교) + setter 7줄 + 로그
 *   - 지금: Schedule.isCreatable(검증) → hasOverlappingSchedule(각 일정에 overlaps 를 물음) → Schedule.draft(생성) → 저장 → 로그
 *   - confirm 도 schedule.confirm(userId) 가 소유자 · 상태를 판단해 true/false
 * [보존] public 메서드 시그니처 전부, 실패 시 null 반환(원인 구분 없음), 감사로그 문자열, 조용히 무시하는 동작,
 *   소유자 null 일정 확정 시 NullPointerException - ScheduleServiceCharacterizationTest 24개가 고정한다.
 * [남은 것] status enum(R07), null 반환 → 원인을 알리는 결과(재설계 단계).
 */
@Service
public class ScheduleService {

    // [이름 개선] repo · userRepo → scheduleRepository · userRepository (약어 금지 규칙)
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    // [리팩토링] 직접 new → 생성자 주입. 이름도 audit → auditLogger
    private final AuditLogger auditLogger;

    public ScheduleService(ScheduleRepository scheduleRepository, UserRepository userRepository, AuditLogger auditLogger) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    /**
     * 일정 생성.
     * [보존] 입력이 잘못됐거나 같은 소유자의 일정과 겹치면 null (저장 · 로그 없음, 예외 없음).
     */
    public Schedule create(String title, Long ownerId, LocalDateTime startAt,
                           LocalDateTime endAt, String location) {
        if (!Schedule.isCreatable(title, startAt, endAt)) {
            return null;
        }
        if (hasOverlappingSchedule(ownerId, startAt, endAt)) {
            return null;   // [보존] 겹쳐서 실패해도 원인 없이 null (재설계 단계 결함 후보)
        }
        Schedule schedule = Schedule.draft(title, ownerId, startAt, endAt, location);
        scheduleRepository.save(schedule);
        auditLogger.write("SCHEDULE CREATE", schedule.getId(), ownerId);
        return schedule;
    }

    /**
     * 같은 소유자의 기존 일정 중 겹치는 것이 있는가.
     * [리팩토링] 레거시 flag1 루프 → 이름 있는 메서드 + isOverlapping (스멜9: flag1 은 무슨 깃발인지 몰랐다).
     * [보존] 레거시처럼 중간에 멈추지 않고 모든 기존 일정을 끝까지 확인한다(검사 도중 예외가 나는 경우까지 같게).
     */
    private boolean hasOverlappingSchedule(Long ownerId, LocalDateTime startAt, LocalDateTime endAt) {
        boolean isOverlapping = false;
        for (Schedule existingSchedule : scheduleRepository.findByOwnerId(ownerId)) {
            if (existingSchedule.overlaps(startAt, endAt)) {
                isOverlapping = true;
            }
        }
        return isOverlapping;
    }

    /**
     * 일정 확정.
     * [보존] 없는 일정 · 없는 사용자 · 본인 아님 · 예정이 아님 → 예외 없이 무시.
     *   사용자는 조회만 하고 쓰지 않는다(없으면 무시하는 레거시 동작 보존).
     */
    public void confirm(Long id, Long userId) {
        Schedule schedule = scheduleRepository.findById(id).orElse(null);
        if (schedule == null) {
            return;
        }
        User requester = userRepository.findById(userId).orElse(null);
        if (requester == null) {
            return;
        }
        if (!schedule.confirm(userId)) {
            return;
        }
        scheduleRepository.save(schedule);
        auditLogger.write("SCHEDULE CONFIRM", schedule.getId(), userId);
    }
}
