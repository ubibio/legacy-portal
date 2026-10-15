# 코드 스멜 종합 목록 (legacy-portal)

> **이 문서는 무엇을·왜 보는가**
> 코드 스멜(Code Smell)은 "당장 버그는 아니지만 고치기 어렵게 만드는 구조적 징후"다. 진단 문서 01~05에 흩어져 있는 스멜을 **한 곳에 ID를 붙여 모은 목록**으로, 리팩토링 작업·커밋·테스트에서 "S03을 고친다"처럼 같은 이름으로 가리키는 **공통 기준표** 역할을 한다.
> 각 스멜의 심각도 판단과 처리 순서는 [진단/06-심각도-표](진단/06-심각도-표.md)에서 다룬다.

- 기준 시점: 커밋 `7d68470` (main) · 근거 문서: [01 구조](진단/01-구조-파악.md) · [02 중복](진단/02-중복-코드.md) · [03 긴 메서드](진단/03-긴-메서드.md) · [04 매직넘버](진단/04-매직넘버.md) · [05 강결합](진단/05-강결합.md)
- 심각도: 🔴 높음 · 🟠 중간 · 🟡 낮음 · ⚪ 리팩토링 범위 밖(보존 / 재설계 단계)

| ID | 스멜 | 심각도 | 주요 위치 | 내용 요약 | 근거 |
|:-:|---|:-:|---|---|:-:|
| S01 | 테스트 부재 (No Tests) | 🔴 | `src/test` 없음 | 테스트 0개. 리팩토링 전후 동작이 같은지 증명할 안전망이 없다 | 01 |
| S02 | 거대 클래스 (God Class) | 🔴 | `ApprovalService` (197줄) | 상태 전이·권한·영속화·메일·감사로그·화면 라벨·금액 등급을 혼자 담당 | 01, 03 |
| S03 | 긴 메서드 · 깊은 중첩 (Long Method) | 🔴 | `ApprovalService.processApproval` `:75-160` | 86줄, 중첩 5단계, 순환 복잡도 약 25. 상신·승인·반려·취소 4개 업무가 한 메서드에 | 03 |
| S04 | 매직넘버 · 원시 타입 집착 (Primitive Obsession) | 🔴 | 상태·처리·유형·우선순위·역할·분류 (Java 45곳) | `s == 1`, `role >= 2`, `1000000` 처럼 의미 없는 숫자로 규칙을 표현 | 04 |
| S05 | 중복 코드 - 감사로그 | 🔴 | 3개 서비스 6곳 | 시각 포맷 + `[시각] 동작 id=.. by=..` 조립이 복사됨, `writeAudit` 반쪽 추출 | 02 D1 |
| S06 | 강결합 - 직접 `new` (DIP 위반) | 🔴 | `SmtpMailSender` ×2, `FileAuditLogger` ×3 | 협력 객체를 서비스가 직접 생성 → 교체·테스트 대체 불가 | 05, 02 D6 |
| S07 | 중복 코드 - 메일 본문 | 🟠 | Approval 3곳, Notice 1곳 | `안녕하세요 {이름}님,` 본문 + `[말머리] 제목` 조립 복사 | 02 D2 |
| S08 | 중복 권한 판정 | 🟠 | `ApprovalService:114, 133`, `NoticeService:56` | `role >= 2`(팀장 이상) 규칙이 3곳에 숫자로 | 02 D3, 04 M5 |
| S09 | 빈약한 도메인 · 기능 욕심 (Anemic Domain / Feature Envy) | 🟠 | `Approval`·`Notice`·`Schedule`·`User`, `ApprovalService:94, 182-188` | 엔티티는 getter/setter뿐, 상태·금액·본인 판정을 서비스가 대신 계산 | 01, 02 D7·D8 |
| S10 | 중첩 깊은 메서드 (추가) | 🟠 | `NoticeService.publish` (중첩 4), `ScheduleService.create` (39줄·중첩 3) | 권한→상태→분류→루프 계단, 검증+겹침검사+저장 한 덩어리 | 03 |
| S11 | 나쁜 이름 (Poor Naming) | 🟠 | `d` `u` `s` `proc` `tmp` `a` `n` `sc` `flag1` `act` | 한 글자·약어 이름. 의미를 주석으로 설명해야 함 | 01, 02 |
| S12 | Entity 직접 노출 · 요청 DTO 부재 | 🟠 | `ApprovalController:23-45`, `UserController` | 요청은 `Map<String,Object>` + 강제 형변환, 응답은 Entity 그대로 | 01 |
| S13 | 긴 파라미터 목록 (Long Parameter List) | 🟡 | `ApprovalService.create` (8개) | 순서 실수 위험. 단 public 시그니처는 유지 대상 | 03 |
| S14 | 상태 라벨 중복 · 표현 로직의 서비스 침범 | 🟡 | `ApprovalService.statusLabel:169`, `NoticeService.statusLabel:76` | 숫자→한글 라벨 if 사다리가 서비스마다 | 02 D5 |
| S15 | 조회-무시 패턴 반복 | 🟡 | 3개 서비스의 `findById(..).orElse(null)` + `return` | 같은 패턴 3곳, `ScheduleService.confirm` 은 조회한 사용자를 쓰지도 않음 | 02 D4 |
| S16 | 주석 스멜 (Comment Smell) | 🟡 | 서비스 전반 | 나쁜 이름·숫자의 의미를 주석으로 변명 (이름·enum으로 대체 대상) | 01, 04 |
| S17 | 숨은 결합 - 시스템 시계 | 🟡 | `LocalDateTime.now()` 13곳 | 시각이 매번 달라 테스트 기대값 고정 불가 | 05 |
| S18 | 정의만 있고 쓰이지 않는 코드값 | 🟡 | 공지 내림(9), 일정 취소(9), 유형 2~4 등 | 해당 상태를 만드는 코드 없음. enum화 시 누락 주의 | 04 |
| S19 | 조용한 실패 · `null` 반환 | ⚪ | `processApproval`·`publish`·`confirm` 무시, `ScheduleService.create` null | 실패 원인을 호출자가 알 수 없음. **레거시 동작이라 리팩토링 중 보존**, 재설계 단계에서 처리 | 01, 03 |
| S20 | 하드코딩된 DB 접속 정보 | ⚪ | `DataSourceConfig:23-25` | 계정·비밀번호 상수. 설정 외부화 문제로 범위 밖 | 05 |

## 보존 대상 요약 (어떤 스멜을 고쳐도 바뀌면 안 되는 것)

- API 엔드포인트·요청/응답 JSON 구조, public 메서드 시그니처(`processApproval(id, userId, action, reason)` 등)
- DB 저장 정수값(0/1/2/3/9 등) - enum은 `@Convert` + `AttributeConverter` 로만
- 조용히 무시·`null` 반환 동작(S19), 메일·감사로그 출력 문자열과 발송 시점
