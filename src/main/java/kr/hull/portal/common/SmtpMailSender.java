package kr.hull.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기.
 * [스멜] 구체 클래스를 서비스가 직접 new 한다(인터페이스 없음). 테스트에서 가짜로 교체 불가.
 * 실습에서는 실제 SMTP 대신 콘솔에 출력만 한다.
 *
 * [리팩토링] MailSender 구현체 + 스프링 빈(@Component)으로 등록 → ApprovalService 는 생성자로 주입받는다 (R03)
 *   - 보존: 출력 형식(=== MAIL === / TO: / SUBJECT: / 본문 / ============) 그대로
 *   - 남은 것: NoticeService 는 아직 직접 new (공지 쪽 특성화 테스트가 없어 이번 범위 밖)
 */
@Component
public class SmtpMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제로는 JavaMailSender 등을 사용. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
