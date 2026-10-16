package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기 - 콘솔 출력 구현체.
 * [스멜] 레거시: 구체 클래스 FileAuditLogger 를 서비스 3곳이 직접 new 했다. 출력 대상(파일/DB/콘솔)을 바꾸려면 호출부를 다 고쳐야 했다.
 *
 * [리팩토링] AuditLogger 구현체 + 스프링 빈(@Component)으로 등록 → 서비스는 생성자로 AuditLogger 를 주입받는다 (R02)
 *   - 이름 변경: FileAuditLogger → ConsoleAuditLogger (기법: 이름 바꾸기 · 이유: 이름은 File 이지만 실제로는 콘솔에 찍었다
 *     (진단 05 문서 "이름과 실제가 다르다"). 파일 기록이 필요해지면 AuditLogger 를 구현한 새 클래스를 만들어 갈아끼운다)
 *   - 보존: 출력 형식 "[AUDIT] " + line 그대로
 *   - 주입받는 곳: ApprovalAuditRecorder(결재), NoticeService(공지), ScheduleService(일정)
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        System.out.println("[AUDIT] " + line);
    }
}
