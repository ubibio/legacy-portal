# REQ-SE-002 요청자 신원 확인 · 상신 권한 검사 재설계

- 진행 현황: 기획중 → 이 문서로 설계안 제시
- 성격: 동작 · API 계약 변경. 리팩토링이 아닌 재설계 단계.
- 현재 결함(특성화 테스트가 고정 중): `ApprovalServiceSafetyNetTest.기안자가_아닌_사람도_상신할_수_있다`, 요청 본문 `userId` · `drafterId` 를 그대로 신뢰, 화면은 `localStorage` 의 사용자 선택

## 1. 인증 방식

| 안 | 내용 | 판단 |
|---|---|---|
| **A. 세션 로그인(권장)** | Spring Security 폼/JSON 로그인 → `HttpSession` 에 사용자 id. 같은 오리진 정적 화면이라 쿠키 세션이 가장 단순 | 교육용 단일 앱에 맞음 |
| B. JWT | 무상태 토큰 | 외부 클라이언트가 없어 이점이 적다 |
| C. 헤더 `X-User-Id` | 신원 확인이 아님 | 요구사항 1번을 만족하지 못함 — 제외 |

- 비밀번호: `User` 에 `passwordHash` 컬럼 추가(BCrypt). `DataSeeder` 초기 4명에 교육용 초기 비밀번호 — **값은 환경변수**로 (REQ-SE-001 원칙)
- `/api/users` 는 로그인 화면이 쓰므로 공개 유지 여부를 결정해야 한다(아래 6절).

## 2. 요청자 결정 지점

- `common/security/CurrentUser` — `@AuthenticationPrincipal` 로 받는 로그인 사용자 id 값 객체
- 컨트롤러가 요청자를 정한다: `process(@PathVariable id, @RequestBody ProcessRequest request, @AuthenticationPrincipal CurrentUser currentUser)`
  → `new ProcessApprovalCommand(currentUser.id(), request.action(), request.reason())`
- 서비스 · 엔티티는 지금처럼 `userId` 를 받는다. **신원은 웹 계층에서 한 번만 확정**하고 아래로는 값으로 넘긴다.
  → `ApprovalService.processApproval(id, userId, action, reason)` public 시그니처는 그대로 둘 수 있다.
- 기안: `drafterId` 를 본문 대신 `currentUser.id()` 로 채운다.

## 3. 본문 `userId` · `drafterId` 이행 단계 (호환)

| 단계 | 서버 | 화면 | 기존 클라이언트 |
|---|---|---|---|
| 1 | 로그인 도입. 본문 `userId` 는 **무시**하고 로그인 사용자 사용. 본문 값이 로그인 사용자와 다르면 경고 로그 | 로그인 화면 + 본문에서 `userId` 제거 | 보내도 무시되므로 깨지지 않음 |
| 2 | 요청 DTO 에서 `userId` · `drafterId` 필드 삭제 (`FAIL_ON_UNKNOWN_PROPERTIES` 기본 false 라 보내도 400 아님) | — | 그대로 동작 |

요구사항 검증 기준 「다른 사용자 ID 를 본문에 넣어도 로그인 사용자로 처리」는 1단계에서 충족된다.

## 4. 상신 권한 규칙 배치

- `Approval.submit(LocalDateTime now)` → `Approval.submit(Long requesterId, LocalDateTime now)` 로 바꾸고, 첫 검사로 `isDraftedBy(requesterId)` (이미 있는 메서드, 취소와 같은 규칙)
- 실패 신호는 REQ-NF-002 와 맞춘다: `NOT_DRAFTER` · 403. NF-002 보다 먼저 적용하면 지금 관례대로 조용히 무시(false).
- 결재 권한(팀장 이상) 판정은 지금처럼 `User.hasApprovalAuthority` 한 곳 — 요구사항 제약 그대로. 상신은 직급과 무관하므로 `User` 에 새 메서드를 두지 않는다.

## 5. 영향받는 기존 테스트 · 계약

| 대상 | 변경 |
|---|---|
| `ApprovalServiceSafetyNetTest.기안자가_아닌_사람도_상신할_수_있다` | 기대값 반전(상태 0 유지) — 별도 커밋으로 diff 제시 |
| 서비스를 부르는 모든 상신 테스트 | 요청자로 기안자를 넘기는지 확인 (대부분 `drafter.getId()` 로 이미 호출) |
| `ApprovalControllerTest` | 모든 요청에 로그인 세션 필요 → `@WithMockUser` 류의 테스트 지원 추가. 본문 · 기대값은 그대로 두고 인증만 더하는 방식으로 |
| `index.html` | 사용자 선택 → 로그인 폼, `localStorage('me')` 제거, 본문 `userId` · `drafterId` 제거 |

## 6. 검증 테스트 목록

| 검증 기준 | 테스트 |
|---|---|
| 다른 사용자 ID 를 본문에 넣어도 로그인 사용자로 처리 | 박팀장으로 로그인 → 본문 `userId: 4` 로 승인 → 승인됨, 감사로그 `by=2` |
| 기안자가 아닌 사용자의 상신 거절 | 최사원으로 로그인 → 김사원의 임시저장 문서 상신 → 상태 0, 메일 · 로그 없음 (NF-002 이후 403) |
| 기안 drafterId = 로그인 사용자 | 본문 `drafterId: 3` 이어도 응답 `drafterId` 가 로그인 사용자 |
| 비로그인 차단 | 세션 없이 `POST /api/approvals` → 401 |

## 7. 남은 결정 (사용자 판단)

- 인증 방식 A안(세션) 채택 여부
- `/api/users` 공개 범위 — 로그인 전 화면이 더 이상 사용자 목록을 쓰지 않으면 인증 뒤로 옮긴다. 결재자 선택 드롭다운이 계속 쓰므로 「로그인 후 공개」 제안
- 교육 환경 초기 비밀번호를 어떻게 배포할지 (환경변수 기본값 없음 → 미설정 시 로그인 불가)
