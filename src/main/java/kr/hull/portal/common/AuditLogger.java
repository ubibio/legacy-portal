package kr.hull.portal.common;

/**
 * 감사로그 기록 추상화.
 *
 * [리팩토링] 레거시: 서비스가 구체 클래스 FileAuditLogger 를 직접 new → 여기서 인터페이스로 추출
 *   (기법: 인터페이스 추출 + 생성자 주입 · 이유: DIP - 출력 대상(파일 · DB · 콘솔)을 바꿔도 서비스는 그대로). 백로그 R02
 * [리팩토링] write(String line) → write(action, id, userId) (기법: 매개변수 객체화 대신 의미 있는 인자로 · 중복 제거)
 *   - 레거시: 결재 · 공지 · 일정 서비스가 저마다 시각 포맷(DateTimeFormatter) + "[시각] 동작 id=.. by=.." 줄을 조립해 넘겼다(복붙 5곳)
 *   - 여기서: 서비스는 "무슨 동작 · 어떤 대상 · 누가"만 넘기고, 줄 형식은 구현체(ConsoleAuditLogger) 한 곳이 만든다
 * [보존] 출력 문자열 "[AUDIT] [yyyy-MM-dd HH:mm:ss] {동작} id={대상id} by={행위자id}" 1글자도 그대로
 *   (ApprovalNotificationOutputTest · NoticeServiceCharacterizationTest · ScheduleServiceCharacterizationTest 가 고정)
 */
// 구현체: ConsoleAuditLogger (콘솔 출력, 실습용). 주입받는 곳: ApprovalAuditRecorder · NoticeService · ScheduleService
public interface AuditLogger {

    /** 감사로그 한 줄. 예: write("NOTICE PUBLISH", 3L, 2L) → "[2026-10-16 13:30:00] NOTICE PUBLISH id=3 by=2" */
    void write(String action, Long id, Long userId);

    /**
     * 끝에 덧붙일 정보가 있는 감사로그. 예: write("APPROVAL CREATE", 1L, 2L, "type=1") → "... id=1 by=2 type=1"
     * [보존] 레거시 결재 생성 로그만 끝에 " type={유형}" 이 붙었다 - 그 출력을 그대로 내기 위한 변형이다.
     */
    void write(String action, Long id, Long userId, String detail);
}
