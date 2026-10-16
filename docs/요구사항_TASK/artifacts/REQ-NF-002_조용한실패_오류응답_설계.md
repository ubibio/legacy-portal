# REQ-NF-002 조용한 실패 → 명시적 오류 응답 재설계

- 진행 현황: 기획중 → 이 문서로 설계안 제시
- 성격: **동작 변경**(재설계). CLAUDE.md 의 리팩토링 불변 규칙이 아니라 「리팩토링 완료 후 별도 단계」에 해당한다.
- 관련: 스멜 S19, REQ-NF-003(입력 검증) · REQ-DA-003(범위 검증)이 이 오류 형식을 재사용

## 1. 무시 분기 전수 목록 (현재 코드 기준)

| # | 위치 | 조건 | 지금 | 오류 코드 | HTTP |
|---|---|---|---|---|---|
| A1 | `ApprovalService.processApproval` | 결재 id 없음 | 조용히 return | `APPROVAL_NOT_FOUND` | 404 |
| A2 | 〃 | 사용자 id 없음 | 조용히 return | `USER_NOT_FOUND` | 404 |
| A3 | 〃 | action 이 1·2·3·9 아님 | 조용히 return | `INVALID_ACTION` | 400 |
| A4 | `Approval.submit` false | 상태 ≠ 임시저장 | 무시 | `INVALID_APPROVAL_STATE` | 409 |
| A5 | `Approval.approve/reject` false | 상태 ≠ 상신 | 무시 | `INVALID_APPROVAL_STATE` | 409 |
| A6 | 〃 | 요청자 ≠ 결재자 | 무시 | `NOT_APPROVER` | 403 |
| A7 | 〃 | 직급 < 팀장 | 무시 | `NO_APPROVAL_AUTHORITY` | 403 |
| A8 | `Approval.cancel` false | 상태가 승인·반려·취소 | 무시 | `INVALID_APPROVAL_STATE` | 409 |
| A9 | 〃 | 요청자 ≠ 기안자 | 무시 | `NOT_DRAFTER` | 403 |
| N1 | `NoticeService.publish` | 공지 없음 | return | `NOTICE_NOT_FOUND` | 404 |
| N2 | 〃 | 사용자 없음 | return | `USER_NOT_FOUND` | 404 |
| N3 | `Notice.publish` false | 직급 < 팀장 | 무시 | `NO_PUBLISH_AUTHORITY` | 403 |
| N4 | 〃 | 상태 ≠ 임시 | 무시 | `INVALID_NOTICE_STATE` | 409 |
| S1 | `ScheduleService.create` | 제목 비었음 · 시각 없음 | null | `REQUIRED_FIELD_MISSING` | 400 |
| S2 | 〃 | 종료 < 시작 | null | `INVALID_TIME_RANGE` | 400 |
| S3 | 〃 | 같은 소유자 일정과 겹침 | null | `SCHEDULE_OVERLAP` | 409 |
| S4 | `ScheduleService.confirm` | 일정 없음 / 사용자 없음 | return | `SCHEDULE_NOT_FOUND` / `USER_NOT_FOUND` | 404 |
| S5 | `Schedule.confirm` false | 요청자 ≠ 소유자 | 무시 | `NOT_SCHEDULE_OWNER` | 403 |
| S6 | 〃 | 상태 ≠ 예정 | 무시 | `INVALID_SCHEDULE_STATE` | 409 |

판정 순서는 지금 엔티티가 검사하는 순서를 따른다(예: 승인은 상태 → 결재자 → 권한). 순서가 응답 코드를 정하므로 테스트로 고정한다.

## 2. 오류 응답 형식 (1종)

```json
{ "code": "NOT_APPROVER", "message": "지정된 결재자만 처리할 수 있습니다." }
```

- `common/error/ErrorResponse(String code, String message)` record
- `common/error/PortalErrorCode` enum — `(HttpStatus status, String message)` 를 갖는다. 위 표의 코드가 상수
- `common/error/PortalException(PortalErrorCode)` — 서비스가 던진다
- `common/error/GlobalExceptionHandler` (`@RestControllerAdvice`) — `PortalException` → `ResponseEntity<ErrorResponse>`
- 필드별 사유가 필요한 REQ-NF-003 은 같은 형식에 `"fieldErrors": [{"field":"drafterId","reason":"필수"}]` 를 **선택 필드**로 더한다(없으면 키 생략 · `@JsonInclude(NON_EMPTY)`)

## 3. 엔티티 → 서비스 신호 방식

지금 엔티티 행위는 `boolean` 을 돌려준다. 사유를 구분하려면 둘 중 하나:

- **A안(권장)** — 엔티티가 실패 시 `PortalException` 을 던진다. `boolean` 반환 제거.
  서비스의 `if (!approval.submit(...)) return;` 이 사라지고 흐름이 단순해진다. 웹 계층 예외가 아니라 도메인 예외이므로 엔티티가 던져도 계층 위반이 아니다.
- B안 — 엔티티가 `Result`(성공 / 실패 사유 enum)를 돌려주고 서비스가 예외로 바꾼다. 엔티티 테스트는 쉬우나 코드가 늘어난다.

## 4. 영향받는 계약

| 대상 | 변경 |
|---|---|
| `POST /api/approvals/{id}/process` | 성공은 **200 + 빈 본문 그대로**. 실패만 4xx + `ErrorResponse` |
| `ScheduleService.create` 반환 | null → 예외. public 시그니처는 그대로(반환 타입 `Schedule`), 의미만 「null 없음」 |
| `index.html` `act()` · `createApproval()` | `r.ok` 가 아니면 `(await r.json()).message` 를 alert. 지금은 처리 결과를 보지 않고 목록만 다시 그린다 |
| 특성화 테스트 | 아래 5절 |

## 5. 특성화 테스트 기대값 변경 — 커밋 분리 계획

1. **커밋 1 (기능)**: 오류 형식 · 예외 · 핸들러 · 엔티티/서비스 변경. 이 시점에 무시 분기 테스트가 실패한다.
   → 실패 목록을 그대로 기록(`docs/…/NF-002_깨진_테스트.md`)하고 **테스트는 손대지 않는다.**
2. **커밋 2 (테스트 기대값)**: 깨진 테스트만 「조용히 무시」 → 「예외 / 4xx」 로 바꾼다. 메서드명도 시나리오에 맞게 바꾼다
   (예: `권한없는_사원이_승인하면_조용히_무시된다` → `권한없는_사원이_승인하면_403이다`). diff 를 리뷰어에게 그대로 보인다.
3. **커밋 3 (화면)**: `index.html` 오류 메시지 표시.

예상 영향 테스트(이름 기준): `ApprovalServiceBranchTest` 의 `조용히_무시` 9개, `ApprovalServiceSafetyNetTest` 6개, `ApprovalServiceTest` 3개,
`ApprovalProcessCharacterizationTest` 의 무시 · 정의 밖 action 4개, `ApprovalControllerTest.없는_결재_id를_처리해도_200과_빈_본문이다`,
`NoticeServiceCharacterizationTest` 5개, `ScheduleServiceCharacterizationTest` 의 null · 무시 13개, `RequirementAcceptanceTest` 의 재상신 1개.

## 6. 검증 테스트 목록

| 검증 기준 | 테스트 |
|---|---|
| 없는 결재 id → 404 + 사유 | MockMvc `post /api/approvals/999999/process` → `status 404`, `$.code == APPROVAL_NOT_FOUND` |
| 사원 승인 → 403 | 결재자로 지정된 사원이 action=2 → `403`, `$.code == NO_APPROVAL_AUTHORITY`, 상태 1 유지 |
| 성공 응답 불변 | `ApprovalControllerTest` 의 성공 케이스 5개 본문 무수정 green |
| 분기별 코드 · 상태 | 1절 표 19행을 `@ParameterizedTest` 로 |
| 실패 시 부수효과 없음 | 4xx 후 메일 · 감사로그 출력 없음 (OutputCapture) — 지금의 「무시 시 로그 없음」 계약을 그대로 이어받는다 |

## 7. 남은 결정 (사용자 판단)

- `INVALID_ACTION` 을 400 으로 둘지(입력 오류) 422 로 둘지 — 요구사항 기본값 표에 없어 400 으로 제안.
- 이미 같은 상태인 요청(재상신 · 재승인)을 409 로 둘지, 멱등 성공(200)으로 둘지 — 표는 409 로 제안.
