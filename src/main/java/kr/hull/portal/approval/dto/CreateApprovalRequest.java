package kr.hull.portal.approval.dto;

import kr.hull.portal.approval.DraftApprovalCommand;

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

    /**
     * 웹 요청 → 도메인 명령 변환.
     * [리팩토링 R12] 레거시: 컨트롤러가 이 DTO 를 8개 값으로 풀어 서비스에 넘겼다 → 여기서 명령 객체 하나로 바꿔 넘긴다.
     *   변환을 DTO 가 맡는 이유: 의존 방향을 dto → 도메인 한쪽으로 유지 (도메인 명령은 웹 DTO 를 모른다).
     * [보존] 필드 생략 시 NullPointerException(500) - type · priority 는 int 로 언박싱, drafterId · approverId 는 longValue() 에서.
     *   레거시 컨트롤러와 같은 순서(type → priority → drafterId → approverId)로 평가된다.
     *   이름에 get 접두가 없어 JSON 직렬화 대상이 아니다.
     */
    public DraftApprovalCommand toCommand() {
        return new DraftApprovalCommand(
                title,
                content,
                type,
                priority,
                drafterId.longValue(),
                approverId.longValue(),
                amount,
                urgent);
    }
}
