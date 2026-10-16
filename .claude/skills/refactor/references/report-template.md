# REPORT.md 양식

아래 뼈대를 채운다. 숫자는 전부 실측, 측정 방법을 함께 적는다.

```markdown
# 리팩토링 전/후 비교 보고서

> <프로젝트> 의 리팩토링 결과를 숫자와 코드로 비교한다.
> 원칙: 내부 구조만 바꾸고 겉보기 동작(API · JSON · DB 저장값 · public 시그니처)은 100% 보존.

| 구분 | 기준 커밋 | 시점 |
|---|---|---|
| Before (레거시) | `<해시>` - 특성화 테스트 직전 | YYYY-MM-DD |
| After (현재) | `<해시>` | YYYY-MM-DD |

## 1. 한눈에 보기

| 지표 | 방향 | Before | After | 결과 |
|---|---|---|---|---|
| 가장 긴 메서드 | ↓ | 86줄 | 21줄 | ✅ −76% |
| 메서드 평균 길이 (1줄 getter/setter 제외) | ↓ | | | |
| 중첩 3단 이상 메서드 | ↓ | | | |
| 핵심 서비스 책임 수 | ↓ | | | |
| 서비스 안 숫자 비교(매직넘버) | ↓ | | | |
| 협력 객체 직접 `new` | ↓ | | | |
| 대표 중복 패턴 (예: 로그 포맷 복붙) | ↓ | | | |
| 테스트 수 | ↑ | 0 | | |
| 줄 / 분기 커버리지 | ↑ | 0% | | |
| 외부 계약 변경 | 0 유지 | - | 0건 | ✅ |
| 특성화 테스트 본문 수정 | 0 유지 | - | 0건 | ✅ |

## 2. 측정 방법
## 3. 메서드 길이 변화 (길었던 메서드 표 + 분포)
## 4. 책임 수 변화 (책임 → Before 위치 → After 위치 → 기법)
## 5. 중복 변화 (패턴 · 종류(동일/유사/우연) · Before · After · 처리)
## 6. 커버리지 변화 (단계별 + 주요 클래스, 놓친 분기의 이유)
## 7. 대표 Before / After (3~4쌍, 레거시 원본에서 복사)
## 8. 보존 확인 (대상 · 확인 방법 · 결과) + 알려진 차이
## 9. 남은 과제 / 재설계 단계로 미룬 것
```

## 측정 명령

```bash
# 레거시 소스 풀기
git archive <레거시 커밋> src/main | tar -x -C <임시폴더>

# 메서드 길이 · 중첩 (두 시점 모두)
node <skill>/scripts/method-metrics.cjs <임시폴더>/src/main/java
node <skill>/scripts/method-metrics.cjs src/main/java

# 분포 요약 (1줄 getter/setter 제외)
node <skill>/scripts/method-metrics.cjs src/main/java | awk -F'\t' '$1>1{n++; s+=$1; if($1>10)l++; if($2>=3)d++; if($1>m)m=$1} END{printf "%d methods avg %.1f max %d >10:%d nest>=3:%d\n",n,s/n,m,l,d}'

# 서비스 안 숫자 비교 (주석 제외)
grep -v '^\s*\*\|^\s*//' <Service>.java | grep -cE '(==|>=|!=|<=) [0-9]'

# 커버리지
./mvnw clean test && bash <skill>/scripts/coverage-summary.sh target/site/jacoco/jacoco.csv
```
