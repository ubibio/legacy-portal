package kr.hull.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [요구사항 인수 테스트 · 시트 갱신분] 요구사항정의서 v3.1.3 갱신 후 수동 확인으로 남아 있던 검증 기준을 자동화한다.
 *
 * - 대응표: docs/요구사항_TASK/검증표.md (★★ 표시)
 * - 기존 테스트(RequirementAcceptanceTest 포함)는 고치지 않고 이 파일에만 더한다. 현재 동작을 고정할 뿐 바꾸지 않는다.
 * - 실제 포트로 띄워야 H2 콘솔 서블릿이 등록되므로 RANDOM_PORT 를 쓴다 (MockMvc 는 서블릿 등록을 타지 않는다).
 * - 테스트 리소스의 application.properties 가 운영 설정 파일을 가리므로(같은 이름), 운영 설정 파일을 직접 얹어
 *   "앱을 그대로 띄웠을 때" 의 H2 콘솔 설정(spring.h2.console.enabled=true)을 확인한다.
 * - 덮어쓰는 값 2개 (리뷰 반영):
 *   · portal.datasource.password 를 비운다 - 개발 PC 에 환경변수가 있어도 MariaDB 에 붙지 않고 반드시 H2 로 뜨게 한다.
 *   · ddl-auto=update - 다른 테스트 컨텍스트와 같은 H2 인메모리 DB 를 쓰므로, create-drop 으로 남의 테이블을 지우지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "file:src/main/resources/application.properties",
        properties = {"portal.datasource.password=", "spring.jpa.hibernate.ddl-auto=update"})
class RequirementUpdateAcceptanceTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java");
    // 시스템 시계를 직접 읽는 호출 - LocalDateTime · LocalDate · LocalTime · Instant 의 인자 없는 now()
    private static final Pattern DIRECT_NOW_CALL = Pattern.compile("\\b(LocalDateTime|LocalDate|LocalTime|Instant)\\s*\\.\\s*now\\(\\s*\\)");
    // 블록 주석 · 줄 주석 - 리팩토링 주석에 남은 "LocalDateTime.now()" 설명은 호출이 아니다
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Autowired
    private TestRestTemplate restTemplate;

    private static boolean hasDirectNowCall(Path sourceFile) {
        try {
            String code = COMMENT.matcher(Files.readString(sourceFile, StandardCharsets.UTF_8)).replaceAll("");
            return DIRECT_NOW_CALL.matcher(code).find();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---- REQ-DA-002 ----

    @Test
    void H2로_뜬_상태에서_h2_console에_접속된다() {
        ResponseEntity<String> response = restTemplate.getForEntity("/h2-console/", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("H2");
    }

    // ---- REQ-NF-004 ----

    @Test
    void 운영_소스에_LocalDateTime_now_직접_호출이_남아_있지_않다() throws IOException {
        assertThat(MAIN_SOURCE_ROOT).as("프로젝트 루트에서 실행해야 한다 (user.dir=%s)", System.getProperty("user.dir"))
                .isDirectory();

        List<Path> directNowCalls;
        try (Stream<Path> sourceFiles = Files.walk(MAIN_SOURCE_ROOT)) {
            directNowCalls = sourceFiles
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(RequirementUpdateAcceptanceTest::hasDirectNowCall)
                    .toList();
        }

        assertThat(directNowCalls).isEmpty();
    }
}
