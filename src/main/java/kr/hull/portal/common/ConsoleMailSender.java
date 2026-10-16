package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기 - 콘솔 출력 구현체.
 * [스멜] 레거시: 구체 클래스 SmtpMailSender 를 서비스가 직접 new 했다(인터페이스 없음). 테스트에서 가짜로 교체 불가.
 *
 * [리팩토링] MailSender 구현체 + 스프링 빈(@Component)으로 등록 → 서비스는 생성자로 MailSender 를 주입받는다 (R03)
 *   - 이름 변경: SmtpMailSender → ConsoleMailSender (기법: 이름 바꾸기 · 이유: 실제로는 SMTP 가 아니라 콘솔에 찍는다.
 *     이름이 하는 일과 같아야 한다. 진짜 SMTP 가 필요해지면 MailSender 를 구현한 새 클래스를 만들어 갈아끼운다)
 *   - 보존: 출력 형식(=== MAIL === / TO: / SUBJECT: / 본문 / ============) 1글자도 그대로
 *   - 주입받는 곳: ApprovalNotifier(결재), NoticeService(공지)
 */
@Component
public class ConsoleMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
