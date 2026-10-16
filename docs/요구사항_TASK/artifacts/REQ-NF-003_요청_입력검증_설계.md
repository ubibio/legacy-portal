# REQ-NF-003 요청 입력 검증 설계

- 진행 현황: 기획중 → 이 문서로 설계안 제시
- 성격: 동작 변경(비정상 요청의 500 → 400). 정상 요청의 응답 · 동작은 그대로.
- 선행: REQ-NF-002 오류 형식(`ErrorResponse` + 선택 필드 `fieldErrors`)

## 1. 현재 동작 (요청 DTO 주석 · REPORT.md 알려진 차이 기준)

| 요청 | 필드 | 생략 시 지금 | 명시적 null 시 지금 |
|---|---|---|---|
| `CreateApprovalRequest` | `type` · `priority` | `toCommand` 언박싱 NPE → 500 | 같음 |
| 〃 | `drafterId` · `approverId` | `longValue()` NPE → 500 | 같음 |
| 〃 | `amount` · `urgent` | 0 · false | 0 · false (레거시는 500 이었음 — 알려진 차이) |
| 〃 | `title` · `content` | null 저장 | null 저장 |
| `ProcessRequest` | `userId` · `action` | NPE → 500 | 같음 |
| 〃 | `reason` | "" | "" |

## 2. 필드별 규칙표

| 요청 | 필드 | 규칙 | 위반 사유 문구 |
|---|---|---|---|
| 기안 | `title` | 필수, 공백만 불가(`@NotBlank`) | `제목은 필수입니다` |
| 기안 | `content` | 선택 | — |
| 기안 | `type` | 필수, 1~4 (`@NotNull @Min(1) @Max(4)`) | `유형은 1~4 입니다` |
| 기안 | `priority` | 필수, 1~3 | `우선순위는 1~3 입니다` |
| 기안 | `drafterId` | 필수 (`@NotNull`) | `기안자 ID 는 필수입니다` |
| 기안 | `approverId` | 필수 | `결재자 ID 는 필수입니다` |
| 기안 | `amount` | 선택(기본 0), 0 이상 (`@PositiveOrZero`) | `금액은 0 이상입니다` |
| 기안 | `urgent` | 선택(기본 false) | — |
| 처리 | `userId` | 필수 | `요청자 ID 는 필수입니다` |
| 처리 | `action` | 필수 (값 범위 1·2·3·9 는 REQ-NF-002 `INVALID_ACTION` 이 판정) | `처리 구분은 필수입니다` |
| 처리 | `reason` | 선택(기본 "") | — |

- `type` · `priority` 범위는 REQ-DA-003 2단계와 같은 규칙이다. DTO 에서 막으면 엔티티의 `fromCode` 예외는 API 경로에서 관찰되지 않는다.

## 3. 명시적 null 과 키 생략을 같게

record 는 둘을 구분하지 못하므로 **같게 다루는 것이 자연스럽다**. 정할 것은 기본값을 언제 채우느냐뿐이다.

- 검증 대상(`@NotNull` 등)은 compact 생성자에서 **기본값을 채우지 않는다** → null 이면 그대로 검증 실패.
- 기본값 필드(`amount` · `urgent` · `reason`)는 지금처럼 compact 생성자에서 채운다 → 생략 · null 모두 기본값.
  따라서 `amount` 의 `@PositiveOrZero` 는 기본값이 채워진 뒤의 값에 걸린다.

## 4. 어디에 두나

- 규칙: 요청 DTO 필드의 Bean Validation 애노테이션 (요구사항 제약 「컨트롤러 곳곳에 흩지 않는다」)
- 발동: 컨트롤러 파라미터 `@Valid @RequestBody` — 컨트롤러에 들어가는 코드는 이 애노테이션 하나
- 의존성: `spring-boot-starter-validation` 추가 (pom.xml)
- 변환: `GlobalExceptionHandler` 에 `MethodArgumentNotValidException` → 400 + `ErrorResponse(code="INVALID_REQUEST", fieldErrors=[...])`
- JSON 파싱 실패(`"type":"abc"`) → `HttpMessageNotReadableException` → 400 `INVALID_REQUEST` (필드 사유 없음)
- `toCommand()` 의 `longValue()` NPE 경로는 검증 뒤엔 도달하지 않는다. 의도를 남기기 위해 그대로 두고 주석만 고친다.

## 5. 영향받는 기존 테스트

| 테스트 | 지금 기대 | 변경 |
|---|---|---|
| `ApprovalControllerTest.금액과_긴급을_생략하면_…` | 200 | 변화 없음 (선택 필드) |
| `ApprovalControllerTest.반려_요청에_reason을_빼면_…` | 200 · "" | 변화 없음 |
| 서비스 레벨 특성화 테스트(null 결재 id · null 사용자 id → 예외) | 저장소 예외 | 변화 없음 — 서비스 직접 호출은 DTO 검증을 거치지 않는다 |

즉 이 요구사항만으로 깨지는 기존 테스트는 없을 것으로 본다(필드 누락 500 을 고정한 API 테스트가 현재 없음). 착수 시 `./mvnw test` 로 확인한다.

## 6. 검증 테스트 목록

| 검증 기준 | 테스트 |
|---|---|
| drafterId 누락 → 400 + 'drafterId' 사유 | `post /api/approvals` (drafterId 없음) → `400`, `$.fieldErrors[?(@.field=='drafterId')]` 존재, 결재 건수 불변 |
| 명시적 null = 생략 | `"drafterId": null` 도 같은 400 |
| 금액 음수 | `"amount": -1` → 400 `amount` |
| 범위 밖 type | `"type": 5` → 400 `type` (REQ-DA-003 검증 기준과 공유) |
| 처리 요청 필수 | `{"action":1}` → 400 `userId` |
| 정상 요청 불변 | `ApprovalControllerTest` 8개 본문 무수정 green |
