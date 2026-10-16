package kr.hull.portal.common;

/**
 * 업무 알림 메일의 공통 양식 - 제목 "[말머리] 제목", 본문 "안녕하세요 {이름}님,\n{안내문}\n제목: {제목}".
 *
 * [리팩토링] 중복 제거 (기법: 함수 추출 + 매개변수화 · 유사 중복 → 한 곳)
 *   - 레거시: 같은 인사말 · 제목 줄 조립이 결재 알림 3곳(상신 · 승인 · 반려)과 긴급 공지 1곳에 복붙돼 있었다
 *     (진단 중복 표 3 · 4번: 안내문 · 말머리만 다른 "유사 중복" - 둘 다 "업무 메일 양식"이라는 같은 지식이다)
 *   - 여기서: 양식은 이 클래스 한 곳, 각 알림은 말머리 · 안내문 · 끝에 붙일 줄만 정한다. 양식이 바뀌면 이 파일만 고친다
 * [보존] 제목 · 본문 문자열 1글자도 그대로 (ApprovalNotificationOutputTest · NoticeServiceCharacterizationTest 가 고정)
 * 상태가 없는 순수 함수라 주입 대상이 아닌 정적 메서드로 둔다 (MailSender 처럼 바꿔 끼울 필요가 없는 "형식" 지식).
 */
public final class MailFormat {

    private MailFormat() {
    }

    /** 제목 - 예: subject("결재요청", "노트북 구매") → "[결재요청] 노트북 구매" */
    public static String subject(String tag, String title) {
        return "[" + tag + "] " + title;
    }

    /** 본문 - 예: body("박팀장", "결재 요청이 도착했습니다.", "노트북 구매") → "안녕하세요 박팀장님,\n결재 요청이 도착했습니다.\n제목: 노트북 구매" */
    public static String body(String recipientName, String message, String title) {
        return "안녕하세요 " + recipientName + "님,\n" + message + "\n제목: " + title;
    }
}
