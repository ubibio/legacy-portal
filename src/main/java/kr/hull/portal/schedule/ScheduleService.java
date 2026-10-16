package kr.hull.portal.schedule;

import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 일정 서비스.
 * [스멜4] 감사 로그 코드가 또 복붙. [스멜] 시간 겹침(중복 예약) 검증이 길고 읽기 어렵다.
 *
 * [리팩토링 R02] 레거시: new FileAuditLogger() 직접 생성
 *   → AuditLogger 인터페이스를 생성자로 주입받는다 (기법: 생성자 주입 · 이유: DIP - 출력 대상을 바꿔도 이 클래스는 그대로)
 *   - 실행 시 주입되는 구현체: ConsoleAuditLogger (레거시와 출력 문자열 동일)
 * [보존] 감사로그 문자열, 검증 · 겹침 판정 · null 반환 · 조용히 무시하는 동작 전부 그대로 - 협력 객체를 얻는 방법만 바뀌었다.
 * [남은 것] 감사로그 복붙 · 긴 검증 · flag1 이름 · 매직넘버는 일정 특성화 테스트를 만든 뒤 다룬다.
 */
@Service
public class ScheduleService {

    private final ScheduleRepository repo;
    private final UserRepository userRepo;
    // [리팩토링] 직접 new → 생성자 주입. 이름도 audit → auditLogger
    private final AuditLogger auditLogger;

    public ScheduleService(ScheduleRepository repo, UserRepository userRepo, AuditLogger auditLogger) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.auditLogger = auditLogger;
    }

    public Schedule create(String title, Long ownerId, LocalDateTime startAt,
                           LocalDateTime endAt, String location) {
        // [스멜2] 검증 + 중복확인 + 저장 + 로그가 한 덩어리.
        if (title == null || title.equals("")) {   // [스멜] "".equals 가 아니라 NPE 위험 패턴
            return null;
        }
        if (startAt == null || endAt == null) {
            return null;
        }
        if (endAt.isBefore(startAt)) {
            return null;
        }
        // [스멜] 같은 사람의 시간 겹침을 직접 루프 돌며 확인(읽기 어려움).
        boolean flag1 = false;   // flag1 = 시간 겹침 여부(true=겹침)  [스멜9: 무슨 깃발? isOverlapping 이어야 함]
        for (Schedule existingSchedule : repo.findByOwnerId(ownerId)) {   // [이름 개선] s → existingSchedule (상태가 아니라 같은 소유자의 기존 일정)
            if (existingSchedule.getStatus() != 9) {   // status(상태): 0 예정·1 확정·9 취소 → 취소(9) 일정은 겹침 검사 제외
                if (existingSchedule.getStartAt().isBefore(endAt) && startAt.isBefore(existingSchedule.getEndAt())) {
                    flag1 = true;       // 시간대가 겹침 (이 조건식 자체도 메서드로 추출 대상)
                }
            }
        }
        if (flag1) {
            return null;   // [스멜] null 반환 — 호출자는 '겹쳐서 실패'인지 원인을 모름
        }

        Schedule sc = new Schedule();   // sc = 일정(Schedule 객체)
        sc.setTitle(title);
        sc.setOwnerId(ownerId);
        sc.setStartAt(startAt);
        sc.setEndAt(endAt);
        sc.setLocation(location);
        sc.setStatus(0);   // status(상태): 0 예정·1 확정·9 취소  [0=예정, 확정 전]
        repo.save(sc);

        // [스멜4] 또 복붙된 감사 로그.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        auditLogger.write("[" + now + "] SCHEDULE CREATE id=" + sc.getId() + " by=" + ownerId);
        return sc;
    }

    public void confirm(Long id, Long userId) {
        Schedule sc = repo.findById(id).orElse(null);   // sc = 일정(Schedule 객체)
        if (sc == null) return;
        User user = userRepo.findById(userId).orElse(null);   // [이름 개선] u → user (조회만 하고 쓰지 않음 - 없으면 무시하는 레거시 동작 보존)
        if (user == null) return;
        if (sc.getOwnerId().equals(userId)) {   // 본인 일정만 확정 가능
            if (sc.getStatus() == 0) {          // status==0 → 예정 상태일 때만
                sc.setStatus(1);   // 1 = 확정 (CONFIRMED)
                repo.save(sc);
                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                auditLogger.write("[" + now + "] SCHEDULE CONFIRM id=" + sc.getId() + " by=" + userId);
            }
        }
    }
}
