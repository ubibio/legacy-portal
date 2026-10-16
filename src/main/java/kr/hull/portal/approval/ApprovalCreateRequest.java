package kr.hull.portal.approval;

/**
 * 결재 생성 요청 (POST /api/approvals 본문).
 *
 * [리팩토링] 레거시: 컨트롤러가 Map<String, Object> 로 받아 (int)·(String) 강제 형변환 → 여기서 요청 DTO(record)로 분리
 *   (기법: 요청 DTO 도입 · 이유: 필드 이름 오타를 컴파일러가 못 잡고, 어떤 필드가 오는지 코드만 봐선 몰랐다(스멜 S12). 백로그 R11)
 * [보존] JSON 필드 이름 · 숫자 code 그대로. 필드를 빼고 보냈을 때의 레거시 결과도 그대로 맞춘다
 *   - amount 생략 → 0, urgent 생략 → false (레거시 getOrDefault)
 *   - type · priority · drafterId · approverId 생략 → NullPointerException(500) (레거시 형변환과 같은 결과)
 * [알려진 차이 - 비정상 요청만] Map 은 "키 없음"과 "값이 null"을 구분했지만 record 는 둘 다 null 이다.
 *   - "amount": null · "urgent": null 을 명시하면 레거시는 500, 지금은 0 · false 로 처리된다
 *   - "type": "1" 처럼 문자열 숫자를 보내면 레거시는 형변환 오류(500), 지금은 Jackson 이 1 로 바꿔 받는다
 */
public record ApprovalCreateRequest(
        String title,
        String content,
        Integer type,
        Integer priority,
        Long drafterId,
        Long approverId,
        Long amount,
        Boolean urgent) {

    /** [보존] 생략 시 0 (레거시 body.getOrDefault("amount", 0)). */
    long amountOrZero() {
        return amount == null ? 0L : amount;
    }

    /** [보존] 생략 시 false (레거시 body.getOrDefault("urgent", false)). */
    boolean isUrgent() {
        return urgent != null && urgent;
    }
}
