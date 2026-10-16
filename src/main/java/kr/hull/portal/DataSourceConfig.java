package kr.hull.portal;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * DB 자동 선택 설정.
 *
 * 시작할 때 MariaDB 에 연결되는지 확인해서,
 *  - 연결되면  → MariaDB 사용(실무감)
 *  - 안 되면   → H2 인메모리 사용(설치 없이 그냥 실행)
 * 수강생이 MariaDB/WSL 세팅을 못 해도 앱이 알아서 H2 로 뜬다. 프로파일·URL 입력 불필요.
 *
 * [리팩토링 · REQ-SE-001] 레거시: MariaDB URL · 계정 · 비밀번호를 private static final 상수로 소스에 박아 두었다(스멜 S20)
 *   → 설정 키(portal.datasource.url · username · password)로 받는다 (기법: 설정 외부화 · 생성자 주입)
 *   - 이유: 비밀번호가 소스 · 커밋 이력에 남았고, 접속 대상을 바꾸려면 코드를 고쳐 다시 빌드해야 했다.
 *   - 우선순위: 환경변수(PORTAL_DATASOURCE_URL · _USERNAME · _PASSWORD) > application.properties - 스프링 기본 규칙(relaxed binding)
 *   - 비밀번호는 application.properties 에도 두지 않는다(빈 값) → 환경변수로만 넣는다.
 * [보존] 설정이 없으면(URL 또는 비밀번호가 비면) 지금처럼 H2 인메모리로 자동 폴백, 2초 접속 제한, 시작 로그 문구, H2 URL(MODE=LEGACY).
 * [달라지는 점 - 운영 환경] 예전 기본 비밀번호(소스 상수)로 MariaDB 에 붙던 환경은 이제 PORTAL_DATASOURCE_PASSWORD 를 넣어야 MariaDB 로 뜬다.
 *   넣지 않으면 H2 로 뜬다(교육 환경 기본 동작과 같다).
 */
@Configuration
public class DataSourceConfig {

    private static final String H2_URL = "jdbc:h2:mem:portal;MODE=LEGACY;DB_CLOSE_DELAY=-1";
    private static final int CONNECTION_CHECK_TIMEOUT_SECONDS = 2;

    // [리팩토링] 상수 → 설정 값. 키가 없을 때(테스트 리소스 등) 빈 문자열이 되도록 기본값 ":" 를 둔다
    private final String mariaDbUrl;
    private final String mariaDbUsername;
    private final String mariaDbPassword;

    public DataSourceConfig(@Value("${portal.datasource.url:}") String mariaDbUrl,
                            @Value("${portal.datasource.username:}") String mariaDbUsername,
                            @Value("${portal.datasource.password:}") String mariaDbPassword) {
        this.mariaDbUrl = mariaDbUrl;
        this.mariaDbUsername = mariaDbUsername;
        this.mariaDbPassword = mariaDbPassword;
    }

    @Bean
    public DataSource dataSource() {
        if (isMariaDbConfigured() && isMariaDbReachable()) {
            System.out.println(">> DB = MariaDB (" + mariaDbUrl + ")");
            return DataSourceBuilder.create()
                    .driverClassName("org.mariadb.jdbc.Driver")
                    .url(mariaDbUrl)
                    .username(mariaDbUsername)
                    .password(mariaDbPassword)
                    .type(HikariDataSource.class)
                    .build();
        }
        System.out.println(">> MariaDB 연결 불가 → H2 인메모리로 실행 (조회: http://localhost:8080/h2-console)");
        return DataSourceBuilder.create()
                .driverClassName("org.h2.Driver")
                .url(H2_URL)
                .username("sa")
                .password("")
                .type(HikariDataSource.class)
                .build();
    }

    /** [REQ-SE-001] 접속 URL 과 비밀번호가 둘 다 있어야 MariaDB 를 시도한다 - 없으면 접속 시도 없이 바로 H2. */
    private boolean isMariaDbConfigured() {
        return !mariaDbUrl.isBlank() && !mariaDbPassword.isBlank();
    }

    /** MariaDB 에 2초 안에 연결되는지 가볍게 확인(없으면 빠르게 H2 로 넘어가도록). */
    private boolean isMariaDbReachable() {
        DriverManager.setLoginTimeout(CONNECTION_CHECK_TIMEOUT_SECONDS);
        try (Connection ignored =
                     DriverManager.getConnection(mariaDbUrl, mariaDbUsername, mariaDbPassword)) {
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
