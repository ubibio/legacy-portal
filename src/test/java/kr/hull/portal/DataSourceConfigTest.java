package kr.hull.portal;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [REQ-DA-002 · REQ-SE-001] 실행 DB 자동 선택과 접속 정보 외부화.
 *
 * - 스프링 없이 DataSourceConfig 를 직접 만들어 어떤 데이터소스를 고르는지 본다 (H2 폴백 · 시작 로그).
 * - 데이터소스는 만들기만 하고 연결하지 않는다(HikariDataSource 는 첫 getConnection 때 연결) → MariaDB 없이도 돈다.
 */
@ExtendWith(OutputCaptureExtension.class)
class DataSourceConfigTest {

    private static final String H2_URL = "jdbc:h2:mem:portal;MODE=LEGACY;DB_CLOSE_DELAY=-1";
    // 아무도 듣지 않는 포트 - 접속이 바로 거절되어 H2 로 넘어간다
    private static final String UNREACHABLE_MARIADB_URL = "jdbc:mariadb://127.0.0.1:1/portal";

    private static String jdbcUrlOf(DataSource dataSource) {
        return ((HikariDataSource) dataSource).getJdbcUrl();
    }

    @Test
    void 아무_설정이_없으면_H2_인메모리로_뜨고_H2_로그를_남긴다(CapturedOutput output) {
        DataSource dataSource = new DataSourceConfig("", "", "").dataSource();

        assertThat(jdbcUrlOf(dataSource)).isEqualTo(H2_URL);
        assertThat(output.getOut()).contains(">> MariaDB 연결 불가 → H2 인메모리로 실행");
    }

    @Test
    void 비밀번호가_없으면_URL이_있어도_접속을_시도하지_않고_H2로_뜬다() {
        DataSource dataSource = new DataSourceConfig("jdbc:mariadb://localhost:3306/portal", "portal", "").dataSource();

        assertThat(jdbcUrlOf(dataSource)).isEqualTo(H2_URL);
    }

    @Test
    void MariaDB에_닿지_않으면_H2로_폴백한다() {
        DataSource dataSource = new DataSourceConfig(UNREACHABLE_MARIADB_URL, "portal", "secret").dataSource();

        assertThat(jdbcUrlOf(dataSource)).isEqualTo(H2_URL);
    }

    @Test
    void 접속_정보는_설정_키에서_읽는다() {
        new ApplicationContextRunner()
                .withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues(
                        "portal.datasource.url=" + UNREACHABLE_MARIADB_URL,
                        "portal.datasource.username=portal",
                        "portal.datasource.password=secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 닿지 않는 주소라 H2 로 폴백 - 설정 키가 바인딩되어 접속을 시도했다는 뜻
                    assertThat(jdbcUrlOf(context.getBean(DataSource.class))).isEqualTo(H2_URL);
                });
    }

    @Test
    void DataSourceConfig_소스에_계정과_비밀번호_문자열이_없다() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/kr/hull/portal/DataSourceConfig.java"), StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("portal1234")
                .doesNotContain("MARIADB_PASSWORD")
                .doesNotContain("MARIADB_USER");
    }
}
