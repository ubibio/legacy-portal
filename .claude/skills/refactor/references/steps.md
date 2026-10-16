# 10단계 상세

각 절은 그 단계를 시작할 때 읽는다. 예시는 legacy-portal(결재 · 공지 · 일정) 실습에서 가져왔다.

## 목차
1. 진단
2. 특성화 테스트
3. 매직넘버 · 식별자
4. 메서드 추출 · 가드 절
5. 계층 · DTO
6. 책임 분리 (SRP)
7. DIP
8. 중복 제거
9. 도메인
10. 보고서

---

## 1. 진단

**목표**: 코드를 바꾸기 전에 "무엇이 문제이고 어떤 순서로 고칠지"를 문서로 남긴다. 코드 변경 없음.

1. 구조 파악 - 패키지 · 클래스 · 의존 방향 · 진입점(API)을 그린다. 협력 객체를 직접 `new` 하는 곳을 표시한다.
2. 메서드 길이 · 중첩 측정 - `scripts/method-metrics.cjs`.
3. 스멜 목록 (`docs/smells.md`) - 번호(S01~) · 이름 · 심각도(★~★★★★★) · 위치(파일:줄) · 왜 문제인가.
   흔한 것: God Class · Long Method · Magic Number · Duplicated Code · Tight Coupling(직접 new) · Feature Envy · Primitive Obsession · Long Parameter List · Poor Naming · Comment Smell · No Tests · Anemic Domain · Silent Failure.
4. 중복은 **동일 / 유사 / 우연**으로 나눠 적는다 (8단계에서 쓴다).
5. 리팩토링 백로그 (R01~) - 순서 · 의존 관계 · 각 항목의 완료 기준(숫자로). R01 은 항상 특성화 테스트.
6. (선택) SonarQube 기준선 - 이슈 수 · 인지 복잡도 · 커버리지 · 중복률 · 보안 등급. 우리 진단과 무엇이 다른지 비교하면 도구의 맹점이 보인다 (예: 매직넘버 · 직접 new 는 Sonar 가 못 잡음).
7. **"재설계 단계로 미룰 것"** 을 따로 적는다 - 조용한 실패 · null 반환 · 하드코딩된 비밀번호처럼 고치면 동작이 바뀌는 것.

## 2. 특성화 테스트

**목표**: "지금 이렇게 동작한다"를 그대로 박제한다. 옳고 그름은 판단하지 않는다.

- 기대값은 **코드를 실행해서 관찰한 값**이다. 추측으로 쓰고 틀리면, 테스트가 아니라 기대값을 관찰 결과로 바꾼다 (이 단계에서만).
- 테스트 종류
  - 서비스: `@DataJpaTest` + `@Import({서비스, 협력 객체들})`, 진짜 H2 저장소. 데이터는 setter 대신 서비스 메서드로 준비 (상태를 setter 로 꾸며야만 하는 경우 - 예: 내림 9 - 는 저장소로 직접).
  - API 계약: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` - URL · 필드 개수(`$.*` hasSize) · 정수 code · 빈 본문.
  - 출력 문자열: `@ExtendWith(OutputCaptureExtension.class)` - 메일 · 감사로그를 1글자까지. 시각처럼 매번 바뀌는 부분은 그 뒤만 비교 (`"] APPROVAL CREATE id=" + id + " by=" + ...`).
  - DB 저장값: JDBC 로 컬럼 직접 조회 (`select status from approval where id = ?`), 그 전에 `entityManager.flush()`. enum 도입 후에도 정수가 저장되는지 지킨다.
- 이상한 동작도 고정한다: 조용히 무시 · null 반환 · `findById(null)` 의 `InvalidDataAccessApiUsageException` · NPE. 테스트 주석에 `[보존 대상 · 결함 후보]`.
- 경계값: `>=` 기준은 기준값 · 기준값-1 둘 다 (예: 1,000,000 / 999,999). 겹침은 맞닿음 · 감쌈 · 취소된 것 · 다른 사람 것.
- JaCoCo 로 빈 분기를 찾아 보강 → 리팩토링 대상의 실행 가능한 분기 100% 근접.
- 이름: 메서드만 한글 시나리오(`권한없는_사원이_승인하면_조용히_무시된다`), 필드 · 변수 · 상수는 영어 (Sonar S116 · S117).
- 정수 리터럴로 호출 · 검증. 역할 · 유형 같은 숫자는 테스트 상수(`TEAM_LEADER_ROLE = 2`)로 이름만 붙인다.

## 3. 매직넘버 · 식별자

**목표**: 숫자의 의미를 타입으로, 약어를 의미 있는 이름으로.

```java
// Before
private int status;   // 0=임시저장 1=상신 2=승인 3=반려 9=취소
if (s == 1) { ... d.setStatus(2); }

// After
@Convert(converter = ApprovalStatusConverter.class)
@Column(nullable = false)
private ApprovalStatus status = ApprovalStatus.DRAFT;

public enum ApprovalStatus {
    DRAFT(0, "임시저장"), SUBMITTED(1, "상신"), APPROVED(2, "승인"), REJECTED(3, "반려"), CANCELED(9, "취소");
    public static ApprovalStatus fromCode(int code) { ... }   // 없는 code 는 예외
}
```

- `getStatus()` 는 계속 `int` 를 돌려준다 (응답 JSON · 특성화 테스트 계약). enum 접근자는 `approvalStatus()` 처럼 get 접두 없이.
- 기본값(레거시 int 기본값 0)과 NOT NULL 을 맞춘다.
- 요청에서 들어오는 코드(action 1/2/3/9)는 `ApprovalAction.fromCode(int)` → `Optional` - 없는 코드는 레거시처럼 조용히 무시.
- 라벨 if 사다리 → enum 필드.
- enum 이름은 도메인을 드러낸다 (`Status` 대신 `ApprovalStatus`).
- 이름 바꾸기는 커밋을 따로 (`refactor: 약어 지역변수를 의미 있는 이름으로 rename`).

## 4. 메서드 추출 · 가드 절

**목표**: 긴 메서드를 이름 있는 작은 메서드로, 깊은 중첩을 조기 return 으로.

```java
// Before - 86줄, 중첩 5
if (proc == 2) { if (s == 1) { if (approverId.equals(userId)) { if (role >= 2) { ...

// After
switch (approvalAction) {
    case SUBMIT -> submit(approval, userId);
    case APPROVE -> approve(approval, requester);
    ...
}
private void approve(Approval approval, User requester) {
    if (!approval.approve(requester, LocalDateTime.now())) {
        return;                       // 가드 절 - [보존] 조건 불만족 → 조용히 무시
    }
    ...
}
```

- 분기 하나 = 메서드 하나. 각 분기의 순서(저장 → 메일 → 로그)를 그대로 옮긴다.
- 가드 절은 조건을 뒤집어 조기 return. 결과가 같은지 진리표로 한 번 확인한다.
- 이 단계에서는 규칙을 엔티티로 옮기지 않는다(9단계). 서비스 안에서 모양만 바꾼다.

## 5. 계층 · DTO

**목표**: 웹 형식과 도메인을 떼어 놓는다. JSON 은 그대로.

- `Map<String, Object>` 요청 → `record` 요청 DTO. 레거시 기본값(`getOrDefault`)은 compact 생성자에서.
- Entity 응답 → 응답 DTO `from(entity)`. 필드 이름 · 순서 · 개수 · 타입을 레거시 JSON 과 같게 (`$.*` hasSize 로 검증).
- 긴 매개변수 목록 → **도메인 명령 객체**(`DraftApprovalCommand`). 웹 DTO 를 서비스에 그대로 넘기지 않는다 - 도메인이 HTTP 형식(박싱 타입 · null)에 묶인다. 변환은 `request.toCommand()` (의존 방향 dto → 도메인).
- 레거시 public 시그니처(`create(8개 인자)`)는 남기고 명령 객체를 만들어 새 메서드로 위임만.
- null 처리 차이를 임시 MockMvc 테스트로 확인 (필드를 하나씩 빼고 보내기).
- DTO 는 `<도메인>.dto` 패키지.

## 6. 책임 분리 (SRP)

**목표**: 클래스가 바뀌는 이유를 하나로.

1. 서비스의 "변경 이유"를 나열한다 - 업무 흐름 / 상태 규칙 / 권한 / 생성 규칙 / 코드 해석 / 알림 문구 / 감사로그 형식 / 표시 라벨.
2. 이유마다 갈 곳을 정한다 - 알림 → `XNotifier`, 감사 → `XAuditRecorder`, 코드 해석 → enum, 생성 규칙 → 정적 팩토리, 규칙 → 엔티티(9단계).
3. 서비스에는 흐름 조율만 남긴다. 클래스 상단 주석에 "변경 이유 → 지금 맡는 곳" 표를 남긴다.
4. 출력 문자열 테스트(메일 · 로그)가 있어야 안전하다.

## 7. DIP

**목표**: 구체 클래스 대신 추상에 의존 - 바꿔 끼울 수 있고 테스트에 가짜를 넣을 수 있게.

```java
// Before
private final SmtpMailSender mail = new SmtpMailSender();

// After
public interface MailSender { void send(String to, String subject, String body); }
@Component public class ConsoleMailSender implements MailSender { ... }   // 이름은 실제 하는 일대로
public XService(..., MailSender mailSender, AuditLogger auditLogger) { ... }
```

- 이름이 거짓말이면 바로잡는다 (`FileAuditLogger` 가 콘솔에 찍으면 `ConsoleAuditLogger`).
- 특성화 테스트 `@Import` 의 클래스 이름이 바뀌면 그 줄만 기계적으로 고치고 diff 를 보여 확인받는다.
- 효과를 테스트로 보여 준다: `FakeMailSender`(호출 기록) · `mock(AuditLogger.class)` 를 생성자로 넣는 단위 테스트 - 실제 발송 없이 send 호출만 검증.
- 같은 협력 객체를 쓰는 다른 서비스도 함께 주입으로 (특성화 테스트가 먼저 있어야 한다).

## 8. 중복 제거

**목표**: 같은 지식을 한 곳에. 우연히 닮은 것은 그대로.

| 종류 | 뜻 | 처리 |
|---|---|---|
| 동일 | 글자까지 같은 복붙 | 한 곳으로 |
| 유사 | 구조 같고 값만 다름, 같은 규칙 | 매개변수화해서 한 곳으로 |
| 우연 | 모양만 같고 바뀌는 이유가 다름 | **합치지 않는다** - 각 도메인으로 |

- 예: 감사로그 시각 포맷 · 줄 조립(6곳) → `ConsoleAuditLogger.write(action, id, userId)` 한 곳. 한 곳만 다른 꼬리(" type=1")가 있으면 덧붙임 인자 변형을 두되 포맷 코드는 하나.
- 인터페이스 시그니처가 바뀌면 mock 검증을 쓰는 단위 테스트도 바뀐다 - 특성화 테스트가 아닌지 확인하고, 바꾼 내용을 보고한다.

## 9. 도메인

**목표**: Tell, Don't Ask - 서비스가 getter 로 꺼내 비교하던 규칙을 데이터가 있는 엔티티로.

```java
// 서비스
if (!approval.approve(requester, now)) return;   // 시키기만
approvalRepository.save(approval);               // 성공했을 때만 부수효과
notifier.notifyApproved(approval);

// 엔티티
public boolean approve(User requester, LocalDateTime now) {
    if (!canBeDecidedBy(requester)) return false;   // 조건이 안 맞으면 아무것도 바꾸지 않고 false
    status = ApprovalStatus.APPROVED;
    updatedAt = now;
    return true;
}
```

- 엔티티 행위는 `boolean` 을 돌려준다 → 서비스는 false 면 조용히 끝 (레거시 "조용히 무시" 보존). 예외로 바꾸지 않는다.
- 생성 초기값 규칙 → 정적 팩토리 `X.draft(...)`.
- 권한 판정 → `User.hasXAuthority()` - 규칙마다 따로 (우연 중복).
- 입력 검증 · 겹침 판정 같은 규칙도 엔티티로 (`Schedule.isCreatable`, `schedule.overlaps(...)`).
- 레거시의 검사 순서 · null 결함 · 루프가 끝까지 도는지까지 유지한다.
- public setter 는 레거시 계약이라 남긴다 (캡슐화는 재설계 단계).
- 매직넘버는 엔티티 안 상수로 이름을 붙이되, 도메인마다 따로 (결재의 9 와 공지의 9 는 다른 뜻).

## 10. 보고서

`references/report-template.md` 양식으로 `REPORT.md` 를 쓴다.

- 모든 숫자는 실측. 레거시는 `git archive <레거시 커밋> src/main | tar -x -C <임시폴더>` 로 풀어 **같은 스크립트**로 잰다.
- 지표마다 방향(↑ 클수록 좋음 · ↓ 작을수록 좋음)과 결과(✅ · ⏸ · ❌)를 표시한다. 나빠진 지표는 숨기지 말고 이유를 적는다 (예: 엔티티에 메서드가 늘어 setter 줄 비중이 커져 줄 커버리지 소폭 하락).
- Before/After 스니펫은 레거시 원본과 대조해서 붙인다 (기억으로 쓰지 않는다).
- 보존 확인 표 (API · JSON · DB 값 · 시그니처 · 출력 문자열 · 예외 동작)와 알려진 차이.
- 남은 과제와 재설계 단계로 미룬 것.
- 고친 뒤 표 열 개수 검증, Mermaid 가 있으면 파서로 검증.
