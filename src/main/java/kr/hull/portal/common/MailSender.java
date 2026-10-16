package kr.hull.portal.common;

/**
 * 메일 발송 추상화.
 *
 * [리팩토링] 레거시: 서비스가 구체 클래스 SmtpMailSender 를 직접 new → 여기서 인터페이스로 추출
 *   (기법: 인터페이스 추출 + 생성자 주입 · 이유: DIP - 서비스는 "메일을 보낸다"에만 의존하고,
 *    실제 SMTP · 콘솔 · 테스트용 가짜 구현은 바깥에서 갈아끼운다). 백로그 R03
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
