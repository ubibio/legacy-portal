# 리팩토링 전/후 비교 보고서

> 「클린코드 & 리팩토링 실전」 legacy-portal 결재 · 공지 · 일정 서비스의 리팩토링 결과를 숫자와 코드로 비교한다.
> **원칙**: 내부 구조만 바꾸고 겉보기 동작(API · JSON · DB 저장값 · public 시그니처 · 조용히 무시하는 동작)은 100% 보존한다.

| 구분 | 기준 커밋 | 시점 |
|---|---|---|
| **Before** (레거시) | `a761639^` - 특성화 테스트를 처음 넣기 직전 | 2026-10-15 |
| 중간 측정 | `137c2d8` - 결재 특성화 테스트 6개 + 메서드 추출 직후 | 2026-10-15 |
| **After** (현재) | 공지 · 일정 도메인 이동 (`7fa5f93`) + processApproval 보강 특성화 15개 | 2026-10-16 |

기간 동안 커밋 24개 (refactor 16 · test 5 · docs 2 · chore 1), 36개 파일 +2,946 / −247 줄.

---

## 1. 한눈에 보기

**방향** 열은 그 지표가 어느 쪽으로 가야 좋은지(↑ 클수록 좋음 · ↓ 작을수록 좋음), **결과** 열은 실제로 그쪽으로 갔는지를 표시한다.

| 지표 | 방향 | Before | After | 결과 |
|---|---|---|---|---|
| 가장 긴 메서드 | ↓ | 86줄 (`processApproval`) | **21줄** (`processApproval`) | ✅ −76% |
| 메서드 평균 길이 (1줄 getter/setter 제외) | ↓ | 11.5줄 (30개) | **6.3줄** (85개) | ✅ −45% |
| 중첩 3단 이상 메서드 | ↓ | 3개 (최대 5단) | **0개** (최대 2단) | ✅ 모두 해소 |
| `ApprovalService` 책임 수 | ↓ | 8개 (God Class) | **1개** (흐름 조율) | ✅ −7 |
| 서비스 3개 안 숫자 비교(매직넘버) | ↓ | 27곳 (결재 19 · 공지 6 · 일정 2) | **0곳** | ✅ 엔티티 · enum 으로 |
| 협력 객체 직접 `new` | ↓ | 5곳 | **0곳** | ✅ 생성자 주입 |
| 감사로그 시각 포맷 복붙 | ↓ | 6곳 | **1곳** | ✅ −5 |
| 메일 인사말 · 제목 양식 중복 | ↓ | 4곳 | **1곳** (`MailFormat`) | ✅ −3 |
| 테스트 수 | ↑ | 0개 | **121개** (특성화 114 + 단위 7) | ✅ +121 |
| 줄 커버리지 (프로젝트 전체) | ↑ | 0% | **90.3%** (419/464) | ✅ +90.3%p |
| 분기 커버리지 (프로젝트 전체) | ↑ | 0% | **94.0%** (140/149) | ✅ +94.0%p |
| 리팩토링 대상 서비스 분기 커버리지 (결재 · 공지 · 일정) | ↑ | 0% | **실행 가능한 분기 100%** | ✅ |
| 외부 계약 변경 (API · JSON · DB 값 · public 시그니처) | 0 유지 | - | **0건** | ✅ 보존 |
| 특성화 테스트 본문 수정 | 0 유지 | - | **0건** | ✅ 보존 |

> processApproval 보강 특성화 15개(`ApprovalProcessCharacterizationTest`)는 이미 지나가던 줄 · 분기를 **다른 입력으로 다시 고정**한 것이라 커버리지 숫자는 그대로다 (임원 승인 · 반려 사유 null · 끝난 상태 · null id · 정의되지 않은 action 여러 값 · 명령 객체 진입점).
>
> 줄 커버리지는 R12 직후 측정(92.2%)보다 조금 낮다. 공지 · 일정 엔티티에 행위 메서드가 생기면서, 테스트가 쓰지 않는 기존 setter 줄의 비중이 커졌기 때문이다. 분기 커버리지는 93.4% → 94.0% 로 올랐다.

---

## 2. 측정 방법

- **메서드 길이 · 중첩 깊이**: 같은 스크립트(`method-metrics.cjs`)로 두 시점의 `src/main/java` 를 각각 측정. 메서드 선언 줄부터 닫는 `}` 까지 줄 수(주석 포함), 중첩은 `if/for/while/switch/else` 블록 깊이.
  - After 의 줄 수에는 학습용 `[리팩토링]` · `[보존]` 주석이 포함돼 있어 실제 코드 줄은 더 짧다.
- **중복**: 두 시점 소스에서 같은 패턴을 grep 으로 셈 (주석 줄 제외).
- **커버리지**: JaCoCo (`./mvnw clean test` → `target/site/jacoco/jacoco.csv`). Before 는 테스트 0개라 0%.
- **SonarQube**: 기준선만 측정됨 (`docs/진단/09-소나큐브-비교.md`). After 재분석은 아직 하지 않았다.

---

## 3. 메서드 길이 변화

### 3-1. 길었던 메서드

| 메서드 | Before 줄 · 중첩 | After 줄 · 중첩 | 무엇을 했나 |
|---|---|---|---|
| `ApprovalService.processApproval` | **86 · 5** | **21 · 1** | 메서드 추출(R08) → 규칙을 엔티티로(R09) → action 을 enum switch 로(R10) |
| └ 상신 · 승인 · 반려 · 취소 분기 | (위 86줄 안) | 서비스 7~8줄 × 4 + 엔티티 8~11줄 × 4 | `submit/approve/reject/cancel` 로 추출, 판정은 `Approval` 이 |
| `ApprovalService.create` | 22 · 0 | 6 · 0 | 생성 규칙 → `Approval.draft` 정적 팩토리, 입력 → `DraftApprovalCommand` |
| `ApprovalService.statusLabel` | 11 · 1 | 4 · 0 | if 사다리 → `ApprovalStatus` enum 라벨 |
| `ApprovalService.amountGrade` | 7 · 1 | 1줄 위임 | if 사다리 → `AmountGrade` enum (Move Method) |
| `ApprovalController.create` | 12 · 0 | 5 · 0 | `Map` + 강제 형변환 8줄 → `request.toCommand()` |
| `NoticeService.publish` | 25 · 4 | **18 · 1** | 권한 · 상태 판정 → `Notice.publish(user)`, 긴급 여부 → `isEmergency()`, 전 직원 메일 → private 메서드 추출 |
| `ScheduleService.create` | 39 · 3 | **13 · 1** | 검증 → `Schedule.isCreatable`, `flag1` 루프 → `hasOverlappingSchedule` + `Schedule.overlaps`, 생성 → `Schedule.draft` |
| `ScheduleService.confirm` | 14 · 2 | 15 · 1 | 소유자 · 상태 판정 → `Schedule.confirm(userId)` (줄 수는 주석 · 중괄호 정리로 비슷) |
| `NoticeService.statusLabel` | 7 · 1 | 1줄 위임 | if 사다리 → `Notice.statusLabel()` (Move Method) |

### 3-2. 분포

| 지표 (1줄 getter/setter 제외) | Before | After |
|---|---|---|
| 메서드 수 | 30 | 85 |
| 평균 길이 | 11.5줄 | 6.3줄 |
| 최장 | 86줄 | 21줄 |
| 10줄 초과 | 9개 | 11개 (학습용 주석 포함 - 실제 코드는 대부분 4~8줄) |
| 중첩 3단 이상 | 3개 | **0개** |

메서드 수가 늘어난 것은 **큰 메서드 하나를 이름 있는 작은 메서드 여러 개로 나눴기 때문**이다 (엔티티 행위 · enum `fromCode` · 알림 · 감사 메서드).

---

## 4. 책임 수 변화 - `ApprovalService` God Class 분해

| # | 책임 (변경 이유) | Before 위치 | After 위치 | 리팩토링 |
|---|---|---|---|---|
| 1 | 결재 업무 흐름 (조회 → 처리 → 저장 → 알림) | `ApprovalService` | **`ApprovalService`** (유일하게 남은 책임) | - |
| 2 | 상태 전이 규칙 (어느 상태에서 무엇이 되나) | `ApprovalService` (if 중첩) | `Approval.submit/approve/reject/cancel` | R09 Tell, Don't Ask |
| 3 | 결재 권한 판정 (`role >= 2`) | `ApprovalService` (2곳 복붙) | `User.hasApprovalAuthority()` | R09 |
| 4 | 생성 초기값 규칙 (긴급 → 우선순위 3, 상태 0) | `ApprovalService.create` | `Approval.draft` 정적 팩토리 | R10 |
| 5 | action 코드(1/2/3/9) 해석 | `ApprovalService` (`proc == 1` …) | `ApprovalAction` enum | R10 |
| 6 | 알림 메일 수신자 · 문구 | `ApprovalService` (3곳 복붙) | `ApprovalNotifier` → `MailSender` | R10 · R03 |
| 7 | 감사로그 형식 | `ApprovalService` (2곳) | `ApprovalAuditRecorder` → `ConsoleAuditLogger` | R10 · R02 |
| 8 | 화면 표시 라벨 · 금액 등급 | `ApprovalService` (if 사다리 2개) | `ApprovalStatus` · `AmountGrade` enum | R06 · Move Method |

결과: `ApprovalService` 는 **바뀌는 이유가 하나**(결재 흐름 순서)만 남았다. 메일 문구가 바뀌면 `ApprovalNotifier` 만, 권한 정책이 바뀌면 `User` 만 고친다.

---

## 5. 중복 변화

| 중복 패턴 | 종류 | Before | After | 처리 |
|---|---|---|---|---|
| 감사로그 시각 포맷 `ofPattern("yyyy-MM-dd HH:mm:ss")` | 동일 | **6곳** (결재 2 · 공지 2 · 일정 2) | **1곳** | `ConsoleAuditLogger` 상수 |
| 감사로그 줄 조립 `"[" + now + "] ..."` | 유사 | **6곳** | **1곳** | `ConsoleAuditLogger.write(action, id, userId)` |
| 협력 객체 직접 `new SmtpMailSender()` · `new FileAuditLogger()` | 동일 | **5곳** | **0곳** | 인터페이스 + 생성자 주입 |
| 승인 · 반려 전제조건 3단 검사 (상태 · 결재자 · 권한) | 동일 | **2곳** | **1곳** | `Approval.canBeDecidedBy` |
| 권한 판정 `role >= 2` | 우연 (게시 권한 ≠ 결재 권한) | 3곳 (서비스) | 2곳 (`User` 안 메서드 2개) | `hasApprovalAuthority()` · `hasNoticePublishAuthority()` - 같은 기준이지만 **다른 규칙이라 의도적으로 합치지 않음** |
| 상태 라벨 if 사다리 | 우연 | 2곳 (서비스) | 0곳 (서비스) | 결재는 `ApprovalStatus` enum, 공지는 `Notice.statusLabel()` - 각자 도메인에 (합치지 않음) |
| 메일 본문 `"안녕하세요 {이름}님,\n..."` | 유사 | 4곳 | **1곳** | `common.MailFormat.subject(말머리, 제목)` · `body(이름, 안내문, 제목)` - 각 알림은 말머리 · 안내문 · 끝에 붙일 줄만 정한다 |

---

## 6. 커버리지 변화

### 6-1. 단계별

| 시점 | 테스트 | 줄 커버리지 (전체) | 분기 커버리지 (전체) |
|---|---|---|---|
| Before (레거시) | 0개 | 0% | 0% |
| 결재 특성화 6개 직후 (`137c2d8`) | 6개 | 40.1% | 25.9% |
| R12 명령 객체 직후 (`e79b953`) | 106개 | 92.2% (404/438) | 93.4% (128/137) |
| 공지 · 일정 도메인 이동 (`7fa5f93`) | 106개 | 90.3% (419/464) | 94.0% (140/149) |
| **After (현재)** - processApproval 보강 15개 | **121개** | **90.3%** (419/464) | **94.0%** (140/149) |

### 6-2. 주요 클래스 (After)

| 클래스 | 줄 | 분기 | 비고 |
|---|---|---|---|
| `ApprovalService` | 100% (57/57) | 90.5% (19/21) | 놓친 2개는 실행될 수 없는 분기: enum `switch` 의 숨은 default, `statusLabel` 의 null (status 컬럼은 NOT NULL) |
| `Approval` (엔티티) | 84.5% (60/71) | **100%** (32/32) | 놓친 줄은 테스트가 안 쓰는 setter |
| `ApprovalNotifier` · `ApprovalAuditRecorder` | 100% | 100% | |
| `NoticeService` | **100%** (30/30) | **100%** (10/10) | 공지 특성화 17개 |
| `Notice` (엔티티) | 81.1% (30/37) | **100%** (12/12) | 놓친 줄은 테스트가 안 쓰는 setter |
| `ScheduleService` | **100%** (30/30) | **100%** (14/14) | 일정 특성화 24개 (겹침 경계값 포함) |
| `Schedule` (엔티티) | 83.8% (31/37) | **100%** (20/20) | 놓친 줄은 테스트가 안 쓰는 setter |
| `ApprovalController` · 요청/응답 DTO | 100% | 50~100% | API 계약 테스트 8개 |

리팩토링 대상(결재 · 공지 · 일정 서비스)의 **모든 실행 가능한 분기**가 테스트로 고정돼 있다.

---

## 7. 대표 Before / After

### 7-1. 긴 메서드 + 매직넘버 + 기능 욕심 → 엔티티 행위 (R08 · R06 · R09 · R10)

**Before** - `ApprovalService.processApproval` 86줄 중 승인 분기 (중첩 5단, 숫자 비교, 메일 · 로그 복붙)

```java
int s = d.getStatus();     // s = status ...
int proc = action;
if (proc == 1) {
    ...
} else if (proc == 2) {     // proc==2 → 승인
    if (s == 1) {           // s==1 → 상신 상태일 때만 승인 가능
        if (d.getApproverId() != null && d.getApproverId().equals(userId)) {
            if (u.getRole() >= 2) {   // role>=2 승인권한
                d.setStatus(2);        // 2 = 승인
                d.setUpdatedAt(LocalDateTime.now());
                repo.save(d);
                User drafter = userRepo.findById(d.getDrafterId()).orElse(null);
                if (drafter != null) {
                    String body = "안녕하세요 " + drafter.getName() + "님,\n"
                            + "결재가 승인되었습니다.\n제목: " + d.getTitle();
                    mail.send(drafter.getEmail(), "[결재승인] " + d.getTitle(), body);
                }
                writeAudit("APPROVAL APPROVE", d.getId(), userId);
            }
        }
    }
} else if (proc == 3) {     // 반려 - 위 3단 검사를 그대로 복붙
```

**After** - 서비스는 시키기만 하고(Tell), 판단은 엔티티가 한다

```java
// ApprovalService
switch (approvalAction) {
    case SUBMIT -> submit(approval, userId);
    case APPROVE -> approve(approval, requester);
    case REJECT -> reject(approval, requester, command.reason());
    case CANCEL -> cancel(approval, userId);
}

private void approve(Approval approval, User requester) {
    if (!approval.approve(requester, LocalDateTime.now())) {
        return;                                   // [보존] 조건 불만족 → 조용히 무시
    }
    approvalRepository.save(approval);            // 성공했을 때만 부수효과
    approvalNotifier.notifyApproved(approval);
    approvalAuditRecorder.recordApproved(approval, requester.getId());
}

// Approval (엔티티)
public boolean approve(User requester, LocalDateTime now) {
    if (!canBeDecidedBy(requester)) {
        return false;
    }
    status = ApprovalStatus.APPROVED;             // DB 에는 converter 가 정수 2 로 저장
    updatedAt = now;
    return true;
}

private boolean canBeDecidedBy(User requester) {  // 승인 · 반려 공통 - 복붙 2곳 → 1곳
    return status == ApprovalStatus.SUBMITTED
            && isApprover(requester.getId())
            && requester.hasApprovalAuthority();
}
```

### 7-2. 직접 `new` + 감사로그 복붙 → 인터페이스 주입 + 형식 한 곳 (R02 · R03)

**Before** - 서비스 3개가 구체 클래스를 직접 만들고, 시각 포맷 · 줄 조립을 각자 복붙

```java
// NoticeService (ApprovalService · ScheduleService 도 같은 모양)
private final SmtpMailSender mail = new SmtpMailSender();
private final FileAuditLogger audit = new FileAuditLogger();
...
String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
audit.write("[" + now + "] NOTICE PUBLISH id=" + n.getId() + " by=" + userId);
```

**After** - 인터페이스를 주입받고, 무엇을 · 어떤 대상에 · 누가만 넘긴다

```java
// NoticeService
public NoticeService(NoticeRepository repo, UserRepository userRepo,
                     MailSender mailSender, AuditLogger auditLogger) { ... }
...
auditLogger.write("NOTICE PUBLISH", n.getId(), userId);

// ConsoleAuditLogger - 감사로그 형식은 이 한 곳에만
private String format(String action, Long id, Long userId) {
    String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    return "[" + now + "] " + action + " id=" + id + " by=" + userId;
}
```

덕분에 테스트에서 가짜를 끼울 수 있게 됐다 (`ApprovalServiceMailUnitTest`: `FakeMailSender` · `mock(AuditLogger.class)`).

### 7-3. Map 요청 + 긴 매개변수 목록 → DTO + 명령 객체 (R11 · R12)

**Before** - 필드 이름이 문자열이라 오타를 컴파일러가 못 잡고, 8개 인자를 순서대로 넘긴다

```java
@PostMapping
public Approval create(@RequestBody Map<String, Object> body) {   // Entity 를 그대로 응답
    return service.create(
            (String) body.get("title"),
            (String) body.get("content"),
            (int) body.get("type"),
            (int) body.get("priority"),
            ((Number) body.get("drafterId")).longValue(),
            ((Number) body.get("approverId")).longValue(),   // drafterId 와 바꿔 넣어도 컴파일됨
            ((Number) body.getOrDefault("amount", 0)).longValue(),
            (boolean) body.getOrDefault("urgent", false));
}
```

**After** - 웹 DTO → 도메인 명령 → 응답 DTO

```java
@PostMapping
public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
    Approval approval = approvalService.create(request.toCommand());
    return ApprovalResponse.from(approval);
}

public record DraftApprovalCommand(String title, String content, int type, int priority,
                                   Long drafterId, Long approverId, long amount, boolean urgent) { }
```

레거시 `create(8개 인자)` 는 public 계약이라 **지우지 않고** 명령 객체로 위임만 한다 → 특성화 테스트 무변경.

### 7-4. 상태 매직넘버 → enum + DB 정수 보존 (R06)

**Before**

```java
private int status;     // 0=임시저장 1=상신 2=승인 3=반려 9=취소 (숫자만 저장 → 의미 증발)
d.setStatus(0);
if (s == 0) tmp = "임시저장"; else if (s == 1) tmp = "상신"; ...
```

**After**

```java
@Convert(converter = ApprovalStatusConverter.class)    // @Enumerated 금지 - ORDINAL 이면 9 가 4 로 어긋남
@Column(nullable = false)
private ApprovalStatus status = ApprovalStatus.DRAFT;

public enum ApprovalStatus {
    DRAFT(0, "임시저장"), SUBMITTED(1, "상신"), APPROVED(2, "승인"), REJECTED(3, "반려"), CANCELED(9, "취소");
}
```

DB 에는 여전히 `0/1/2/3/9` 가 저장된다 (`ApprovalStatusPersistenceTest` 가 JDBC 로 컬럼을 직접 읽어 확인).

---

## 8. 보존 확인 (바뀌지 않은 것)

| 대상 | 확인 방법 | 결과 |
|---|---|---|
| API URL · HTTP 메서드 | `ApprovalControllerTest` 8개 | 동일 |
| 요청 / 응답 JSON (필드 12개, 정수 code) | `ApprovalControllerTest` | 동일 |
| DB 저장값 0/1/2/3/9 | `ApprovalStatusPersistenceTest` (JDBC 직접 조회) · 공지 · 일정 특성화 | 동일 |
| public 시그니처 (`create` 8개 · `processApproval` 4개 등) | 특성화 테스트가 그대로 호출 | 동일 |
| 메일 · 감사로그 문자열 | `ApprovalNotificationOutputTest` · 공지 · 일정 특성화 (출력 1글자 비교) | 동일 |
| 조용히 무시 · null 반환 · 예외 동작 | 특성화 114개 | 동일 |
| 필드 생략 시 500 (NPE) | R12 때 임시 테스트로 6가지 확인 | 동일 |

특성화 테스트 본문(호출 · 기대값) 수정 **0건**. 바뀐 것은 클래스 이름 변경에 따른 import · `@Import` 줄뿐이다.

알려진 차이 (비정상 요청만): `"amount": null` · `"urgent": null` · `"reason": null` 을 **명시**하면 레거시는 500 또는 null 저장, 지금은 기본값으로 처리 (record 가 "키 없음"과 "null" 을 구분 못 함). 정상 요청에는 영향 없음.

---

## 9. 남은 과제

| 과제 | 대상 | 비고 |
|---|---|---|
| R07 type · priority · category · role · 공지/일정 status enum | `Approval` · `Notice` · `Schedule` · `User` | **리팩토링 단계에서는 보류.** 지금은 범위 밖 숫자(예: type 5, priority 7)도 그대로 저장되고 응답된다. 필드를 enum 으로 바꾸면 그런 값을 거절하게 되어 **동작 변경**이다. 엔티티 안 이름 붙인 상수로 의미는 드러냈다. enum 전환은 입력 검증과 함께 재설계 단계에서 |
| R13 `Clock` 주입 | `LocalDateTime.now()` | 시각 고정 테스트 가능하게 |
| SonarQube 재분석 | 기준선 이슈 40 · 인지 복잡도 52 · 커버리지 0% · 보안 E | After 수치 기록 |
| 재설계 단계 (동작 변경) | 조용히 무시 → 예외, null 반환, 상신 시 기안자 검사, null id 예외 | 리팩토링 범위 밖 - 테스트로 결함 후보만 표시해 둠 |
