# REQ-IF-003 공지 · 일정 REST API 설계

- 진행 현황: 기획중 → 이 문서로 설계 확정안 제시
- 근거 코드: `notice/NoticeService.java` · `schedule/ScheduleService.java` · 결재 API 의 DTO 방식(`approval/dto/*`, `ApprovalController`)

## 1. 범위

서비스 계층에만 있는 공지 · 일정 기능에 컨트롤러와 record DTO 를 더한다. **서비스 · 엔티티 · 업무 규칙은 고치지 않는다.**
컨트롤러는 요청 DTO → 서비스 public 메서드 호출 → 응답 DTO 변환만 한다.

## 2. 엔드포인트 확정안

| 기능 | 메서드 · URL | 요청 DTO | 서비스 호출 | 응답 |
|---|---|---|---|---|
| 공지 작성 | `POST /api/notices` | `CreateNoticeRequest(title, content, category, writerId, pinned)` | `NoticeService.create(...)` | `200` + `NoticeResponse` |
| 공지 게시 | `POST /api/notices/{id}/publish` | `PublishNoticeRequest(userId)` | `NoticeService.publish(id, userId)` | `200` + 빈 본문 |
| 공지 목록 | `GET /api/notices` | — | `NoticeRepository.findAll()` 를 감싼 `NoticeService.findAll()` 신규 | `200` + `List<NoticeResponse>` |
| 일정 등록 | `POST /api/schedules` | `CreateScheduleRequest(title, ownerId, startAt, endAt, location)` | `ScheduleService.create(...)` | 성공 `200` + `ScheduleResponse`, 거부(null) `200` + 빈 본문 |
| 일정 확정 | `POST /api/schedules/{id}/confirm` | `ConfirmScheduleRequest(userId)` | `ScheduleService.confirm(id, userId)` | `200` + 빈 본문 |
| 내 일정 | `GET /api/schedules/owner/{userId}` | — | `ScheduleService.findByOwner(userId)` 신규 (저장소 `findByOwnerId` 위임) | `200` + `List<ScheduleResponse>` |

결정 근거:

- 게시 · 확정은 결재 `process` 와 같이 **결과와 관계없이 200 + 빈 본문**으로 연다. 무시 분기를 오류로 바꾸는 일은 REQ-NF-002 몫이고, 여기서 미리 바꾸면 그 요구사항의 「특성화 테스트 기대값 변경 커밋 분리」가 깨진다.
- 일정 등록 거부도 같은 이유로 지금은 빈 본문이다. REQ-NF-002 적용 때 400 · 409 + 사유로 바뀐다.
- 조회용 신규 메서드 2개(`findAll` · `findByOwner`)는 **추가**라서 기존 public 시그니처 보존 규칙에 걸리지 않는다. 컨트롤러가 저장소를 직접 쓰지 않게 서비스에 둔다.

## 3. 응답 DTO

엔티티의 getter 와 같은 이름 · 같은 타입의 필드만 둔다. 정수 코드는 정수 그대로다(REQ-DA-001).

```java
public record NoticeResponse(Long id, String title, String content, int status, int category,
                             Long writerId, boolean pinned, LocalDateTime createdAt) {
    public static NoticeResponse from(Notice notice) { ... }
}

public record ScheduleResponse(Long id, String title, int status, Long ownerId,
                               LocalDateTime startAt, LocalDateTime endAt, String location) {
    public static ScheduleResponse from(Schedule schedule) { ... }
}
```

- 패키지: `notice.dto` · `schedule.dto` (결재 `approval.dto` 와 같은 배치)
- `statusLabel()` · `isEmergency()` 같은 도메인 메서드는 응답에 넣지 않는다. 화면에 라벨이 필요해지면 별도 필드로 결정한다.

## 4. 요청 DTO 기본값 · 누락 처리

| 필드 | 생략 시 | 근거 |
|---|---|---|
| `pinned` | `false` | 결재 `urgent` 와 같은 규칙(compact 생성자) |
| `category` · `writerId` · `ownerId` · `userId` | `toCommand`/호출 시 NPE → 500 | 결재 API 의 현재 동작과 맞춘다. 400 검증은 REQ-NF-003 에서 한꺼번에 도입 |
| `startAt` · `endAt` (ISO-8601 문자열) | null 그대로 서비스로 → 거부(null) | `Schedule.isCreatable` 이 이미 null 을 거부한다 |

## 5. 구현 순서 (한 단계 한 커밋)

1. `NoticeService.findAll` · `ScheduleService.findByOwner` 추가 (서비스 단위 테스트 1개씩)
2. `notice.dto` · `NoticeController` + MockMvc 테스트
3. `schedule.dto` · `ScheduleController` + MockMvc 테스트
4. `index.html` 에 공지 · 일정 탭을 붙일지는 별도 요구사항으로 분리 (이 요구사항 범위 밖)

## 6. 검증 테스트 목록

| 검증 기준 | 테스트 |
|---|---|
| API 로 등록한 공지 · 일정이 서비스 특성화 테스트와 같은 결과 | `NoticeControllerTest`: 사원 게시 → 상태 0 유지, 긴급 게시 → 사용자 수만큼 메일 / `ScheduleControllerTest`: 10:00~11:00 뒤 11:00~12:00 등록 · 10:30~11:30 빈 본문 |
| 응답 JSON 에 DTO 필드만 | `jsonPath("$.*", hasSize(8))` (공지) · `hasSize(7)` (일정), `statusLabel` · `emergency` 키 없음 |
| 감사로그 · 메일 동작 불변 | API 호출 후 `NOTICE CREATE` · `SCHEDULE CONFIRM` 출력 1줄 (OutputCapture) |
| 기존 특성화 테스트 | `NoticeServiceCharacterizationTest` · `ScheduleServiceCharacterizationTest` 본문 무수정 green |

## 7. 남은 결정 (사용자 판단)

- URL 은 요구사항 초안 경로를 그대로 채택했다. 복수형 · 하위 리소스 규칙은 결재 API 와 같다.
- 공지 목록에 정렬(상단 고정 우선 · 최신순)을 넣을지: 지금은 저장 순서. 넣으면 응답 순서가 바뀌므로 화면이 생기기 전에 정한다.
