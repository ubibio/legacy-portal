package kr.hull.portal.common;

/**
 * 감사 로그 기록기.
 * [스멜] 역시 구체 클래스 직접 new. 출력 대상(파일/DB/콘솔)을 바꾸려면 호출부를 다 고쳐야 한다.
 */
public class FileAuditLogger {

    public void write(String line) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
