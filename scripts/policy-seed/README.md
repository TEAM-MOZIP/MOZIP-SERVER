# policy-seed

로컬 DB용 Flyway 시드 SQL을 생성하는 스크립트 모음.  
현재 두 가지 데이터 소스를 지원한다.

## 데이터 소스

| 소스 | API | 예상 건수 | SQL 파일 |
|---|---|---|---|
| **정부24** | 행정안전부 공공서비스(혜택) (`gov24/v3`) | ~2,000건 | `R__seed_gov24_policies.sql` |
| **복지로** | 한국사회보장정보원 복지서비스목록정보 (B460011) | ~1,000~2,000건 | `R__seed_bokjiro_policies.sql` |

두 SQL이 모두 `R__`(repeatable) 마이그레이션으로, local DB에 같이 적재된다.

---

## 흐름

```text
# 정부24
fetch_gov24.py  →  raw/serviceList.json + serviceDetail.json + supportConditions.json
                 →  transform_to_sql.py
                 →  src/main/resources/db/localdata/R__seed_gov24_policies.sql

# 복지로
fetch_bokjiro.py  →  raw/bokjiro_list.json
                  →  transform_bokjiro_to_sql.py
                  →  src/main/resources/db/localdata/R__seed_bokjiro_policies.sql
```

---

## 1. 정부24 수집 (GOV24_API_KEY 필요)

공공데이터포털 [활용신청](https://www.data.go.kr): **행정안전부_대한민국 공공서비스(혜택) 정보**  
→ 일반 인증키(Decoding) 사용.

```bash
export GOV24_API_KEY='...'
python3 scripts/policy-seed/fetch_gov24.py
python3 scripts/policy-seed/transform_to_sql.py
```

## 2. 복지로 수집 (BOKJIRO_API_KEY 필요)

공공데이터포털 [활용신청](https://www.data.go.kr): **한국사회보장정보원_복지서비스목록정보**  
(제공기관 코드 B460011, 검색어: "복지로 복지서비스목록")  
→ 일반 인증키(Decoding) 사용.

```bash
export BOKJIRO_API_KEY='...'
python3 scripts/policy-seed/fetch_bokjiro.py
python3 scripts/policy-seed/transform_bokjiro_to_sql.py
```

`raw/`는 .gitignore 대상이다. 인증키는 커밋하지 않는다.

---

## 3. local 프로필에 적용

`application-local.yml`은 gitignore 대상이라 각자 아래처럼 `db/localdata`를 추가해야 한다.

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/testdata,classpath:db/localdata
```

prod(`application-prod.yml`)에는 추가하지 않는다.

---

## 필터링 규칙

### 공통 (두 소스 모두 적용)

| 규칙 | 동작 |
|---|---|
| 서울특별시 / 서울 자치구 / 전국(중앙행정기관·공공기관) | 포함 |
| 다른 지역 | 제외 |
| 법인/시설/단체 전용 | 제외 |
| 군인·현역병·선원 전용 키워드 블랙리스트 | 제외 |

### 정부24 추가 규칙

| 규칙 | 동작 |
|---|---|
| 서비스분야 `농림축산어업` | 제외 |
| `사용자구분` 에 개인·가구·소상공인 없음 | 제외 |

### 복지로 추가 규칙

| 규칙 | 동작 |
|---|---|
| `callTp=P`(개인) / `callTp=H`(가구) 로만 수집 | 법인·단체 사전 제외 |
| `sttCd=02`(종료) | CLOSED 상태로 포함 |
| `ctpvNm` 이 `전체`/`전국`/빈값 | NATIONAL |
| `ctpvNm` 이 `서울` | REGIONAL + SEOUL(_구코드) |

---

## 카테고리 매핑

### 정부24 (`서비스분야`)

| 원본 | MOZIP |
|---|---|
| 주거·자립 | HOUSING |
| 보육·교육 | EDUCATION |
| 고용·창업 | EMPLOYMENT |
| 문화·환경 | CULTURE |
| 생활안정·보건·의료·임신·출산·보호·돌봄·행정·안전 | WELFARE |
| 제목·요약에 창업 키워드 포함 | + STARTUP 추가 |

### 복지로 (`intrsThemaCd`)

| 코드 | 테마명 | MOZIP |
|---|---|---|
| 001 | 생활안정 | WELFARE |
| 002 | 주거 | HOUSING |
| 003 | 고용/창업 | EMPLOYMENT |
| 004 | 보건의료 | WELFARE |
| 006 | 임신/출산 | WELFARE |
| 007 | 보육/교육 | EDUCATION |
| 008 | 노후 | WELFARE |
| 009 | 장애/보훈 | WELFARE |
| 010 | 문화/환경/여가 | CULTURE |
| 011 | 저소득/취약계층 | WELFARE |

---

## 재실행

`R__`(repeatable) 마이그레이션이라 SQL이 바뀌면 local에서 다시 실행된다.  
이때 해당 소스 출처 정책을 지우고 다시 넣으므로 **해당 정책에 걸린 local 북마크·알림도 함께 삭제된다.**  
V5 더미 정책은 건드리지 않는다.
