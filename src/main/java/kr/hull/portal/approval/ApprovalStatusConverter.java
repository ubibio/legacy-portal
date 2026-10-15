package kr.hull.portal.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * ApprovalStatus ↔ DB 정수 code 변환.
 *
 * [리팩토링] 레거시는 int 를 그대로 저장 → enum 도입 후에도 같은 정수가 저장되도록 이 변환기를 둔다
 *   (기법: JPA AttributeConverter · 이유: DB 저장값 변경 금지 규칙).
 * [보존] 저장 컬럼 값은 레거시와 동일한 0/1/2/3/9.
 */
@Converter
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalStatus.fromCode(code);
    }
}
