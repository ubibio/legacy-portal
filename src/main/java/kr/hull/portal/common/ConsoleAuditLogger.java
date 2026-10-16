package kr.hull.portal.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 감사 로그 기록기 - 콘솔 출력 구현체.
 * [스멜] 레거시: 구체 클래스 FileAuditLogger 를 서비스 3곳이 직접 new 했다. 출력 대상(파일/DB/콘솔)을 바꾸려면 호출부를 다 고쳐야 했다.
 *
 * [리팩토링] AuditLogger 구현체 + 스프링 빈(@Component)으로 등록 → 서비스는 생성자로 AuditLogger 를 주입받는다 (R02)
 *   - 이름 변경: FileAuditLogger → ConsoleAuditLogger (기법: 이름 바꾸기 · 이유: 이름은 File 이지만 실제로는 콘솔에 찍었다
 *     (진단 05 문서 "이름과 실제가 다르다"). 파일 기록이 필요해지면 AuditLogger 를 구현한 새 클래스를 만들어 갈아끼운다)
 *   - 주입받는 곳: ApprovalAuditRecorder(결재), NoticeService(공지), ScheduleService(일정)
 * [리팩토링] 감사로그 형식을 이 클래스 한 곳으로 모음 (기법: 중복 코드 제거 · 함수 옮기기)
 *   - 레거시: 시각 포맷 DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") 과 "[" + now + "] 동작 id=.. by=.." 조립이
 *     결재(ApprovalAuditRecorder) · 공지(NoticeService 2곳) · 일정(ScheduleService 2곳)에 복붙돼 있었다 (중복 표 1 · 2번: 동일 · 유사 중복)
 *   - 여기서: 시각 포맷 상수 1개 + format() 1개. 감사로그 형식이 바뀌면 이 파일만 고친다
 * [보존] 출력 "[AUDIT] [yyyy-MM-dd HH:mm:ss] {동작} id={id} by={userId}" (+ " {detail}") 1글자도 그대로.
 *   id · userId 가 null 이면 레거시 문자열 연결처럼 "null" 로 찍힌다.
 * [리팩토링 R13 · REQ-NF-004] 기록 시각 LocalDateTime.now() → LocalDateTime.now(clock) (주입받은 Clock)
 *   - 기본 생성자는 시스템 기본 Clock (Clock 빈이 없는 슬라이스 테스트 · 직접 new). Clock 빈이 있으면 스프링이 Clock 생성자를 고른다.
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Clock clock;

    public ConsoleAuditLogger() {
        this(Clock.systemDefaultZone());
    }

    // Clock 빈이 없으면 스프링은 이 생성자를 건너뛰고 기본 생성자를 쓴다 (required = false)
    @Autowired(required = false)
    public ConsoleAuditLogger(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void write(String action, Long id, Long userId) {
        print(format(action, id, userId));
    }

    @Override
    public void write(String action, Long id, Long userId, String detail) {
        print(format(action, id, userId) + " " + detail);
    }

    private String format(String action, Long id, Long userId) {
        String now = LocalDateTime.now(clock).format(TIMESTAMP_FORMAT);
        return "[" + now + "] " + action + " id=" + id + " by=" + userId;
    }

    private void print(String line) {
        System.out.println("[AUDIT] " + line);
    }
}
