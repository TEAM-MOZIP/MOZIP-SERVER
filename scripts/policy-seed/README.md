# policy-seed

정부24 **행정안전부_대한민국 공공서비스(혜택) 정보** API(`gov24/v3`)에서 정책을 받아
local DB용 Flyway 시드 SQL을 만드는 일회성 스크립트다. 서버 코드와는 무관하다.

## 흐름

```text
fetch_gov24.py  →  raw/*.json  →  transform_to_sql.py  →  src/main/resources/db/localdata/R__seed_gov24_policies.sql
```

## 1. 수집 (인증키 필요, 로컬 터미널에서 실행)

공공데이터포털에서 해당 API 활용신청 후 **일반 인증키(Decoding)** 사용.

```bash
export GOV24_API_KEY='...'
python3 scripts/policy-seed/fetch_gov24.py
```

`raw/`는 .gitignore 대상이다. 인증키는 커밋하지 않는다.

## 2. 변환

```bash
python3 scripts/policy-seed/transform_to_sql.py            # 상태 기준일 = 오늘
python3 scripts/policy-seed/transform_to_sql.py --today 2026-10-01
```

`raw/report.txt`에서 필터 통계, 카테고리 분포, 자격조건 채움률, 신청기간 파싱 실패 목록을 확인한다.

## 3. local 프로필에 적용

`application-local.yml`은 gitignore 대상이라 각자 아래처럼 `db/localdata`를 추가해야 한다.

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/testdata,classpath:db/localdata
```

prod(`application-prod.yml`)에는 추가하지 않는다.

## 매핑 규칙

| 원본 | MOZIP |
|---|---|
| 소관기관유형 중앙행정기관·공공기관 | `region_scope = NATIONAL` |
| 소관기관명 `서울특별시 ○○구` / `서울특별시…` | `REGIONAL` + `SEOUL_○○` / `SEOUL` |
| 그 외 지자체, 농림축산어업 분야, 법인/시설/단체 전용 | 제외 |
| 서비스분야 | 주거·자립→HOUSING, 보육·교육→EDUCATION, 고용·창업→EMPLOYMENT(+창업 키워드 시 STARTUP), 문화·환경→CULTURE, 나머지→WELFARE |
| 신청기한 `상시…` | `ALWAYS` / `ALWAYS_OPEN` |
| 신청기한 `YYYY.MM.DD~YYYY.MM.DD` | `PERIOD` + 날짜, 종료일 지남 → `CLOSED` |
| 그 외 신청기한 문구 | `UNKNOWN` / `OPEN`, 원문은 `application_notes` |
| JA0110/JA0111 (연령) | `minimum_age`/`maximum_age` (0, 100 이상은 제한 없음) |
| JA0101/JA0102 (성별) | 한쪽만 Y일 때 `gender_condition` |
| JA0201~JA0205 (중위소득 구간) | 일부만 Y일 때 `MEDIAN_PERCENTAGE` 하한/상한 |
| JA0326/JA0327 (근로자/구직자) | 한쪽만 Y이고 "해당사항없음"이 아닐 때 `allowed_employment_statuses` |
| 1인가구·한부모·장애인 | 가구 대상 플래그가 전부 이 셋 안에 있을 때만 `allowed_household_types` |
| 선정기준, 나머지 대상 플래그 | `additional_conditions.selectionCriteria` / `targetTags` |

대상 플래그(JA03xx, JA04xx)는 "그 대상에게만 준다"는 뜻이 아니라 "그 대상을 배제하지 않는다"에 가깝다.
그래서 추천에서 오탈락이 생기지 않도록 allowed 목록은 보수적으로만 채운다.

## 재실행

`R__`(repeatable) 마이그레이션이라 SQL이 바뀌면 local에서 다시 실행된다.
이때 gov.kr 출처 정책을 지우고 다시 넣으므로 **해당 정책에 걸린 local 북마크·알림도 함께 삭제된다.**
V5 더미 정책은 건드리지 않는다.
