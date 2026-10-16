package kr.hull.portal.common;

/**
 * 감사로그 기록 추상화.
 *
 * [리팩토링] 레거시: 서비스가 구체 클래스 FileAuditLogger 를 직접 new → 여기서 인터페이스로 추출
 *   (기법: 인터페이스 추출 + 생성자 주입 · 이유: DIP - 출력 대상(파일 · DB · 콘솔)을 바꿔도 서비스는 그대로). 백로그 R02
 */
public interface AuditLogger {

    void write(String line);
}
