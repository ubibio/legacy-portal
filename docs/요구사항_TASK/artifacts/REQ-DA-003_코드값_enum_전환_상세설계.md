# REQ-DA-003 나머지 코드값 enum 전환 · 범위 검증 상세 설계

- 진행 현황: 설계중 → 이 문서로 상세 설계 · 스키마 확정
- 기준 구현: `approval/ApprovalStatus.java` + `ApprovalStatusConverter.java` + `Approval.getStatus()/setStatus(int)/approvalStatus()/changeStatus()`
- 관련: 리팩토링 백로그 R07(보류), REPORT.md 9장

## 1. enum 6종

| enum | 소속 엔티티 · 필드 | 상수(code, label) | 정의 밖 code |
|---|---|---|---|
| `ApprovalType` | `Approval.type` | `EXPENSE(1,"지출")` `LEAVE(2,"휴가")` `PURCHASE(3,"구매")` `OTHER(4,"기타")` | 거절 대상 |
| `ApprovalPriority` | `Approval.priority` | `LOW(1,"낮음")` `NORMAL(2,"보통")` `HIGH(3,"높음")` | 거절 대상 |
| `NoticeStatus` | `Notice.status` | `DRAFT(0,"임시")` `PUBLISHED(1,"게시")` `TAKEN_DOWN(9,"내림")` | 읽기: 아래 3절 |
| `NoticeCategory` | `Notice.category` | `GENERAL(1,"일반")` `EMERGENCY(2,"긴급")` `PERSONNEL(3,"인사")` | 거절 대상 |
| `ScheduleStatus` | `Schedule.status` | `PLANNED(0,"예정")` `CONFIRMED(1,"확정")` `CANCELED(9,"취소")` | 읽기: 아래 3절 |
| `UserRole` | `User.role` | `STAFF(1,"사원")` `TEAM_LEADER(2,"팀장")` `EXECUTIVE(3,"임원")` | 거절 대상 |

- 이름은 CLAUDE.md 규칙대로 소속 도메인을 드러낸다(`Status` · `Role` 단독명 금지).
- 각 enum: `getCode()` · `getLabel()` · `static fromCode(int)` (정의 밖이면 `IllegalArgumentException`, ApprovalStatus 와 같음).
- 각 enum 마다 `XxxConverter implements AttributeConverter<Xxx, Integer>` (`@Converter`, autoApply 없음 · 필드에 `@Convert` 명시).
- **`@Enumerated` 금지** — ORDINAL 이면 `TAKEN_DOWN`/`CANCELED` 가 9 가 아니라 2 로 저장된다.

## 2. 엔티티 전환안 (JSON · 테스트 계약 유지)

결재 상태에서 이미 검증한 패턴을 그대로 쓴다.

```java
@Convert(converter = ApprovalTypeConverter.class)
@Column(nullable = false)
private ApprovalType type;

public int getType() { return type.getCode(); }                 // [보존] JSON "type": 1 · 특성화 테스트 계약
public void setType(int type) { this.type = ApprovalType.fromCode(type); }
public ApprovalType approvalType() { return type; }             // get 접두 없음 → JSON 필드 안 생김
```

| 엔티티 | 바뀌는 내부 상수 | 함께 정리되는 곳 |
|---|---|---|
| `Approval` | `EXPENSE_TYPE` · `HIGH_PRIORITY` → `ApprovalType.EXPENSE` · `ApprovalPriority.HIGH` | `draft` · `submit` |
| `Notice` | `DRAFT_STATUS` 등 4개 → `NoticeStatus` · `NoticeCategory` | `statusLabel()` 이 `status.getLabel()` 로 (요구사항 4번) |
| `Schedule` | `PLANNED_STATUS` 등 3개 → `ScheduleStatus` | `overlaps` · `confirm` |
| `User` | `TEAM_LEADER_ROLE` → `UserRole` 에 `isAtLeast(UserRole)` | `hasApprovalAuthority` · `hasNoticePublishAuthority` (두 메서드는 합치지 않는다 - 우연 중복) |

`ApprovalResponse` · 사용자 응답은 getter 가 정수를 돌려주므로 변경 없음.

## 3. 범위 밖 코드 — 어디서 거절하나

| 경로 | 지금 | 전환 후 | 동작 변경? |
|---|---|---|---|
| DB → 엔티티 (Converter `convertToEntityAttribute`) | int 그대로 읽힘 | `fromCode` 예외 | 정상 데이터엔 영향 없음. 기존 DB 에 정의 밖 값이 있으면 읽기 실패 → **전환 전 점검 SQL 필수** (5절) |
| API 요청 → 엔티티 (`setType(5)` 등) | 저장됨 | 예외 | **예** — 요구사항 3번 「거절」 |
| 테스트 준비 코드 `setStatus(2)` (Notice 라벨 테스트) | "알수없음" | 예외 | **예** — 특성화 테스트가 깨짐 |

마지막 줄이 핵심이다. `NoticeServiceCharacterizationTest.상태_라벨은_0임시_1게시_9내림_그밖은_알수없음이다` 는 `setStatus(2)` 로 정의 밖 값을 넣는다.
그래서 이 요구사항은 **리팩토링 단계로는 끝낼 수 없고** 동작 변경이 들어간다. 처리 방안:

- **A안(권장) — 두 단계로 나눈다.**
  1. 리팩토링 커밋: enum · Converter 도입, setter 는 정의 밖 code 를 **받아 두는** 관대한 경로 유지
     (예: 엔티티는 enum 필드 + `fromCodeOrNull` 로 매핑 실패 시 원래 int 를 보관하지 않는다 → 불가능.
      따라서 Notice · Schedule status 는 1단계에서 int 필드 유지, label 만 enum 으로 위임)
  2. 동작 변경 커밋(REQ-NF-003 과 묶음): 요청 DTO 에서 범위 검증 → 400, 엔티티 setter 를 `fromCode` 로 엄격화,
     해당 특성화 테스트 기대값을 「예외」로 바꾸는 별도 커밋 (REQ-NF-002 의 커밋 분리 원칙)
- B안 — 한 번에 엄격화. 커밋 수는 적지만 「테스트 본문 무수정 green」 검증이 불가능해져 비권장.

## 4. REQ-NF-003 과의 적용 순서

1. REQ-NF-002 오류 응답 형식 확정 (`ErrorResponse`)
2. REQ-NF-003 요청 DTO 검증 도입 — 여기서 `type ∈ {1..4}` · `priority ∈ {1..3}` 범위 검사를 **DTO 에서** 400 으로 막는다
3. 이 요구사항 2단계 — 엔티티 엄격화는 DTO 를 통과한 값만 오므로 API 에선 관찰되지 않는 안전장치가 된다

## 5. 전환 전 점검 SQL (운영 DB 가 있다면)

```sql
SELECT 'approval.type', type, COUNT(*) FROM approval WHERE type NOT IN (1,2,3,4) GROUP BY type
UNION ALL SELECT 'approval.priority', priority, COUNT(*) FROM approval WHERE priority NOT IN (1,2,3) GROUP BY priority
UNION ALL SELECT 'notice.status', status, COUNT(*) FROM notice WHERE status NOT IN (0,1,9) GROUP BY status
UNION ALL SELECT 'notice.category', category, COUNT(*) FROM notice WHERE category NOT IN (1,2,3) GROUP BY category
UNION ALL SELECT 'schedule.status', status, COUNT(*) FROM schedule WHERE status NOT IN (0,1,9) GROUP BY status
UNION ALL SELECT 'users.role', role, COUNT(*) FROM users WHERE role NOT IN (1,2,3) GROUP BY role;
```

결과가 0행이어야 2단계를 적용한다. (현재 실습 환경은 `create-drop` 이라 해당 없음)

## 6. 검증 테스트 목록

| 검증 기준 | 테스트 |
|---|---|
| 전환 후에도 JDBC 컬럼 값 동일 | `CodeValuePersistenceTest` — 6개 필드 각각 저장 후 `JdbcTemplate.queryForObject("select type from approval where id=?")` = 전환 전 정수 (`ApprovalStatusPersistenceTest` 와 같은 방식). 특히 `notice.status=9` · `schedule.status=9` |
| JSON 정수 유지 | `ApprovalControllerTest` 무수정 green (`$.type` · `$.priority` 정수) |
| type=5 기안 거절 · 미저장 | (2단계) `POST /api/approvals {"type":5}` → 400, `approvalRepository.count()` 불변 |
| enum 단위 | 각 enum `fromCode` 정상 · 정의 밖 예외, label 문자열 |
| 기존 특성화 테스트 | 1단계: 전부 본문 무수정 green / 2단계: 기대값을 바꾼 테스트만 별도 커밋으로 diff 제시 |
