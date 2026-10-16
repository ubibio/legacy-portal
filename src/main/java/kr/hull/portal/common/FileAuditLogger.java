package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기.
 * [스멜] 역시 구체 클래스 직접 new. 출력 대상(파일/DB/콘솔)을 바꾸려면 호출부를 다 고쳐야 한다.
 *
 * [리팩토링] AuditLogger 구현체 + 스프링 빈(@Component)으로 등록 → ApprovalService 는 생성자로 주입받는다 (R02)
 *   - 보존: 출력 형식 "[AUDIT] " + line 그대로
 *   - 남은 것: NoticeService · ScheduleService 는 아직 직접 new (공지 · 일정 특성화 테스트가 없어 이번 범위 밖)
 */
@Component
public class FileAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
