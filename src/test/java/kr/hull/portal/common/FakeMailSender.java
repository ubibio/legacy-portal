package kr.hull.portal.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 가짜 메일 발송기 (Fake).
 *
 * - 실제로 보내지도, 콘솔에 찍지도 않고 send 호출 내용만 목록에 쌓는다.
 * - MailSender 인터페이스를 추출(R03)했기 때문에 가능해진 것: 서비스 코드를 고치지 않고 생성자에 이 객체를 넣으면 된다.
 * - Mock 대신 Fake 를 쓴 이유: "누구에게 · 어떤 제목 · 어떤 본문"을 그대로 꺼내 읽을 수 있어 검증이 읽기 쉽다.
 */
public class FakeMailSender implements MailSender {

    /** send 한 번 = 메일 한 통. */
    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> sentMails = new ArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sentMails.add(new SentMail(to, subject, body));
    }

    public List<SentMail> getSentMails() {
        return List.copyOf(sentMails);
    }
}
