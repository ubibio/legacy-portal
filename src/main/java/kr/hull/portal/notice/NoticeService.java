package kr.hull.portal.notice;

import kr.hull.portal.common.AuditLogger;
import kr.hull.portal.common.MailFormat;
import kr.hull.portal.common.MailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 공지 서비스.
 * [스멜4 핵심] ApprovalService 와 거의 동일한 "감사 로그/메일 본문" 로직이 또 복붙되어 있다.
 *             → Day2 '중복 코드 제거 자동화 (공통 모듈 추출)' 실습의 주재료.
 * [스멜5] 역시 협력 객체를 직접 new.  → 해결 (아래 [리팩토링 R02 · R03])
 *
 * [리팩토링 R02 · R03] 레거시: new SmtpMailSender() · new FileAuditLogger() 직접 생성
 *   → MailSender · AuditLogger 인터페이스를 생성자로 주입받는다 (기법: 생성자 주입 · 이유: DIP - 구현체(콘솔 · SMTP · 테스트용 가짜)를 바깥에서 갈아끼움)
 *   - 실행 시 주입되는 구현체: ConsoleMailSender · ConsoleAuditLogger (레거시와 출력 문자열 동일)
 * [리팩토링] 감사로그 시각 포맷 · 줄 조립 복붙 2곳 → auditLogger.write(동작, id, 행위자) 위임 (형식은 ConsoleAuditLogger 한 곳)
 * [리팩토링] 게시 규칙을 엔티티로 이동 (기법: Tell, Don't Ask · 기능 욕심 해소 · 결재 R09 와 같은 방식)
 *   - 레거시 publish: user.getRole() >= 2 · n.getStatus() == 0 · n.getCategory() == 2 를 서비스가 꺼내 비교(중첩 4단)
 *   - 지금: notice.publish(user) 가 권한 · 상태를 판단해 true/false, 긴급 여부는 notice.isEmergency(),
 *     생성 초기값은 Notice.draft, 라벨은 notice.statusLabel() - 서비스는 흐름(조회 → 시키기 → 저장 → 메일 → 로그)만 맡는다
 * [보존] public 메서드 시그니처 전부, 메일 · 감사로그 문자열, 저장 → 메일 → 로그 순서, 조용히 무시하는 동작,
 *   null id 예외(findById(null)) - NoticeServiceCharacterizationTest 17개가 고정한다.
 * [리팩토링] 긴급공지 메일의 인사말 · 제목 양식 → common.MailFormat (결재 알림과의 유사 중복 제거, 문자열은 그대로)
 * [남은 것] status · category enum(R07) - 범위 밖 숫자를 거절하게 되면 동작 변경이라 보류 (REPORT.md 9장).
 */
@Service
public class NoticeService {

    // [이름 개선] repo · userRepo → noticeRepository · userRepository (약어 금지 규칙)
    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    // [리팩토링] 직접 new → 생성자 주입. 이름도 mail · audit → mailSender · auditLogger (타입이 하는 일을 드러냄)
    private final MailSender mailSender;
    private final AuditLogger auditLogger;

    public NoticeService(NoticeRepository noticeRepository, UserRepository userRepository,
                         MailSender mailSender, AuditLogger auditLogger) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.auditLogger = auditLogger;
    }

    /** 공지 생성 (임시). [리팩토링] 초기값 규칙(상태 0 · 생성 시각)은 Notice.draft 가 정한다. */
    public Notice create(String title, String content, int category, Long writerId, boolean pinned) {
        Notice notice = Notice.draft(title, content, category, writerId, pinned, LocalDateTime.now());
        noticeRepository.save(notice);
        auditLogger.write("NOTICE CREATE", notice.getId(), writerId);
        return notice;
    }

    /**
     * 공지 게시.
     * [리팩토링] 중첩 4단 if → 엔티티에 시키고(publish) false 면 조용히 끝, 성공했을 때만 부수효과.
     * [보존] 없는 공지 · 없는 사용자 · 권한 없음 · 임시가 아님 → 예외 없이 무시.
     */
    public void publish(Long id, Long userId) {
        Notice notice = noticeRepository.findById(id).orElse(null);
        if (notice == null) {
            return;   // [보존] 없는 공지 - 조용히 리턴
        }
        User publisher = userRepository.findById(userId).orElse(null);
        if (publisher == null) {
            return;   // [보존] 없는 사용자 - 조용히 리턴
        }
        if (!notice.publish(publisher)) {
            return;   // [보존] 권한 없음(사원) · 이미 게시 · 내림 - 조용히 리턴
        }
        noticeRepository.save(notice);
        if (notice.isEmergency()) {
            notifyAllMembersOfEmergency(notice);
        }
        auditLogger.write("NOTICE PUBLISH", notice.getId(), userId);   // [보존] 메일을 다 보낸 뒤 로그
    }

    // [리팩토링] Extract Method - 긴급공지 전 직원 메일. [보존] 수신자 = 전체 사용자(findAll 순서), 제목 · 본문 1글자도 그대로
    private void notifyAllMembersOfEmergency(Notice notice) {
        for (User member : userRepository.findAll()) {
            mailSender.send(member.getEmail(), MailFormat.subject("긴급공지", notice.getTitle()),
                    MailFormat.body(member.getName(), "긴급 공지가 게시되었습니다.", notice.getTitle()));
        }
    }

    /** 상태 표시 라벨. [보존] public 시그니처 그대로. [리팩토링] 라벨 규칙은 Notice.statusLabel 로 이동 (Move Method). */
    public String statusLabel(Notice notice) {
        return notice.statusLabel();
    }
}
