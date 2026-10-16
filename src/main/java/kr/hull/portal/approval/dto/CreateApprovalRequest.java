package kr.hull.portal.approval.dto;

/**
 * 결재 생성 요청 (POST /api/approvals 본문).
 *
 * [리팩토링] 레거시: 컨트롤러가 Map<String, Object> 로 받아 (int)·(String) 강제 형변환 → 요청 DTO(record)로 분리
 *   (기법: 요청 DTO 도입 · 이유: 필드 이름 오타를 컴파일러가 못 잡고, 어떤 필드가 오는지 코드만 봐선 몰랐다(스멜 S12). 백로그 R11)
 *   - 위치: approval.dto 패키지 (요청 · 응답 DTO 를 엔티티 · 서비스와 분리)
 * [보존] JSON 필드 이름 · 숫자 code 그대로. 필드를 빼고 보냈을 때의 레거시 결과도 그대로 맞춘다
 *   - amount 생략 → 0, urgent 생략 → false (레거시 getOrDefault) - compact 생성자에서 채운다
 *   - type · priority · drafterId · approverId 생략 → 컨트롤러에서 NullPointerException(500), 레거시 형변환과 같은 결과
 * [알려진 차이 - 비정상 요청만] Map 은 "키 없음"과 "값이 null"을 구분했지만 record 는 둘 다 null 이다.
 *   - "amount": null · "urgent": null 을 명시하면 레거시는 500, 지금은 0 · false 로 처리된다
 *   - "type": "1" 처럼 문자열 숫자를 보내면 레거시는 형변환 오류(500), 지금은 Jackson 이 1 로 바꿔 받는다
 */
public record CreateApprovalRequest(
        String title,
        String content,
        Integer type,
        Integer priority,
        Long drafterId,
        Long approverId,
        Long amount,
        Boolean urgent) {

    /** compact 생성자 - [보존] 생략된 amount 는 0, urgent 는 false (레거시 기본값). */
    public CreateApprovalRequest {
        amount = amount == null ? 0L : amount;
        urgent = urgent != null && urgent;
    }
}
