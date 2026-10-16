package kr.hull.portal.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각의 출처(Clock) 빈.
 *
 * [리팩토링 R13 · REQ-NF-004] 레거시: ApprovalService · NoticeService · ConsoleAuditLogger 가 LocalDateTime.now() 를 직접 불렀다
 *   → "지금 몇 시인가"를 Clock 빈 하나로 모으고 주입받는다 (기법: 숨은 의존성(시스템 시계)을 드러내 주입 · DIP)
 *   - 이유: now() 를 직접 부르면 테스트가 시각을 고정할 수 없어 createdAt · 감사로그 시각을 "대충 지금"으로만 확인했다.
 *     테스트는 Clock.fixed(...) 를 넣어 정확한 값으로 비교한다 (ClockInjectionTest).
 * [보존] 운영 시각 의미 그대로 - 시스템 기본 시간대(레거시 LocalDateTime.now() 와 같은 기준). 감사로그 형식도 그대로.
 * 슬라이스 테스트(@DataJpaTest)에는 이 설정이 없다 → 주입받는 쪽은 Clock 빈이 없으면 시스템 기본 Clock 으로 동작한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
