package kr.hull.portal.approval;

import kr.hull.portal.common.ConsoleAuditLogger;
import kr.hull.portal.common.ConsoleMailSender;
import kr.hull.portal.user.User;
import kr.hull.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [특성화 테스트] ApprovalStatus enum 도입 후에도 DB 컬럼에 레거시 정수 code(0/1/2/3/9)가 저장되는지 고정한다.
 * 엔티티를 거치지 않고 JDBC 로 컬럼 값을 직접 읽는다 - enum 이름("DRAFT")이나 순번(CANCELED=4)이 저장되면 실패한다.
 */
@DataJpaTest
@Import({ApprovalService.class, ApprovalNotifier.class, ApprovalAuditRecorder.class, ConsoleMailSender.class, ConsoleAuditLogger.class})
class ApprovalStatusPersistenceTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private User drafter;
    private User teamLeader;

    @BeforeEach
    void saveUsers() {
        drafter = userRepository.save(new User("김사원", "kim@hull.kr", 1, "개발1팀"));
        teamLeader = userRepository.save(new User("박팀장", "park@hull.kr", 2, "개발1팀"));
    }

    private Object storedStatusColumn(Long approvalId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("select status from approval where id = ?", Object.class, approvalId);
    }

    private Approval createApproval() {
        return approvalService.create("노트북 구매", "개발용 노트북", 1, 2,
                drafter.getId(), teamLeader.getId(), 1_200_000L, false);
    }

    @Test
    void 임시저장_결재는_DB에_정수_0으로_저장된다() {
        Approval approval = createApproval();

        assertThat(storedStatusColumn(approval.getId())).isEqualTo(0);
    }

    @Test
    void 승인된_결재는_DB에_정수_2로_저장된다() {
        Approval approval = createApproval();
        approvalService.processApproval(approval.getId(), drafter.getId(), 1, null);
        approvalService.processApproval(approval.getId(), teamLeader.getId(), 2, "");

        assertThat(storedStatusColumn(approval.getId())).isEqualTo(2);
    }

    @Test
    void 취소된_결재는_DB에_순번_4가_아니라_정수_9로_저장된다() {
        Approval approval = createApproval();
        approvalService.processApproval(approval.getId(), drafter.getId(), 9, null);

        assertThat(storedStatusColumn(approval.getId())).isEqualTo(9);
    }
}
