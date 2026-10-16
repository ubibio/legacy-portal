package kr.hull.portal.user;

import jakarta.persistence.*;

/**
 * 사용자 엔티티.
 * [스멜] role 이 int. 1=사원, 2=팀장, 3=임원 — 의미가 코드 곳곳에 매직넘버로 흩어진다.
 *
 * [리팩토링] 결재 권한 판정(role >= 2)을 서비스 → 엔티티 메서드 hasApprovalAuthority() 로 이동
 *   (기법: 기능 욕심 해소 · Rich Domain · 이유: 같은 권한 규칙이 ApprovalService 승인 · 반려에 복붙돼 있었다). 백로그 R09
 *   - 보존: 판정 기준 팀장(2) 이상 그대로. "has" 접두라 JSON 직렬화 대상이 아니다(사용자 응답 필드 5개 유지)
 *   - 남은 것: role 은 아직 int (UserRole enum → R07), NoticeService 의 같은 판정은 공지 특성화 테스트가 없어 그대로
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private int role;       // 1=사원 2=팀장 3=임원 (서비스의 role>=2 판정에 쓰이는 매직넘버 → enum 후보)
    private String dept;

    private static final int TEAM_LEADER_ROLE = 2;   // [리팩토링] 매직넘버 2 에 이름을 붙임 (팀장)

    public User() {}

    /** 결재(승인 · 반려) 권한 - 팀장(2) 이상. [보존] 레거시 "role >= 2" 판정 그대로. */
    public boolean hasApprovalAuthority() {
        return role >= TEAM_LEADER_ROLE;
    }

    public User(String name, String email, int role, String dept) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.dept = dept;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getRole() { return role; }
    public void setRole(int role) { this.role = role; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
}
