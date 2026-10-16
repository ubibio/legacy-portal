package kr.hull.portal.common;

/**
 * 메일 발송기.
 * [스멜] 구체 클래스를 서비스가 직접 new 한다(인터페이스 없음). 테스트에서 가짜로 교체 불가.
 * 실습에서는 실제 SMTP 대신 콘솔에 출력만 한다.
 */
public class SmtpMailSender {

    public void send(String to, String subject, String body) {
        // 실제로는 JavaMailSender 등을 사용. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
