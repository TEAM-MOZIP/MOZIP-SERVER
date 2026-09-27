#!/usr/bin/env python3
"""raw/bokjiro_odcloud.json 또는 bokjiro.csv(한국사회보장정보원_복지서비스정보) → MOZIP 스키마용 Flyway SQL 변환.

사용법:
    python3 scripts/policy-seed/transform_bokjiro_csv_to_sql.py [--today YYYY-MM-DD]

출력:
    src/main/resources/db/localdata/R__seed_bokjiro_csv_policies.sql
    scripts/policy-seed/raw/bokjiro_csv_report.txt

설계 메모
- CSV 컬럼: 서비스아이디, 서비스명, 서비스URL, 서비스요약, 사이트, 대표문의, 소관부처명, 소관조직명, 기준연도, 최종수정일
- 모두 중앙부처(중앙행정기관) 서비스 → 전부 NATIONAL scope.
- 연령·소득·신청기간 필드 없음 → application_type=ALWAYS, eligibility 비움.
- 카테고리: 서비스명 + 서비스요약 텍스트에서 키워드로 추론.
- 비개인 대상(군인·선원·법인 전용) 서비스는 키워드 블랙리스트로 제외.
- 서비스URL이 bokjiro.go.kr 직링크이므로 source_url 로 그대로 사용.
- Repeatable migration(R__)이라 SQL이 바뀌면 local DB에서 재실행된다.
  재실행 시 해당 source_url 패턴으로 기존 레코드를 지우고 다시 넣는다.
"""
import argparse
import csv
import json
import re
from collections import Counter
from datetime import date, datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW_DIR = HERE / "raw"
SERVER_ROOT = HERE.parent.parent
CSV_PATH = RAW_DIR / "bokjiro.csv"
JSON_PATH = RAW_DIR / "bokjiro_odcloud.json"  # fetch_bokjiro_csv.py 결과 (우선)
OUT_SQL = SERVER_ROOT / "src/main/resources/db/localdata/R__seed_bokjiro_csv_policies.sql"
REPORT = RAW_DIR / "bokjiro_csv_report.txt"

# bokjiro.go.kr URL 공통 prefix (DELETE 대상 판별용)
SOURCE_URL_LIKE = "https://www.bokjiro.go.kr/%"
SOURCE_URL_ALT_LIKE = "https://bokjiro.go.kr/%"

# ─────────────────────────────────────────────────────────────────── 카테고리 매핑
# CSV에 테마 코드가 없으므로, 제목+요약 텍스트에 아래 키워드가 있으면 해당 카테고리로 분류.
# 먼저 매칭된 규칙이 우선(순서 중요).

CATEGORY_RULES: list[tuple[str, list[str]]] = [
    # 고용·창업
    ("창업|스타트업|벤처|일자리|취업|채용|고용|취약계층\\s*일|장애인\\s*일자리|청년\\s*일|근로|직업훈련|직업능력|구직|직장", ["EMPLOYMENT"]),
    # 주거
    ("주거|임대|전세|월세|주택|아파트|기숙사|하숙|보증금", ["HOUSING"]),
    # 교육·보육
    ("교육|장학|학습|학비|수업료|유치원|어린이집|보육|방과\\s*후|학교|학원", ["EDUCATION"]),
    # 문화·환경·여가
    ("문화|스포츠|체육|공연|예술|여가|관광|환경|생태|녹색", ["CULTURE"]),
    # 복지 (기본값으로도 쓰임)
    ("복지|의료|건강|질환|장애|장해|재활|노인|노후|출산|임신|육아|돌봄|양육|보호|아동|청소년|저소득|취약|기초생활|생계|긴급|지원금|급여|수당|연금|보험|산재", ["WELFARE"]),
]
CATEGORY_RE: list[tuple[re.Pattern, list[str]]] = [
    (re.compile(pat, re.IGNORECASE), cats) for pat, cats in CATEGORY_RULES
]

STARTUP_RE = re.compile(r"창업|스타트업|벤처|소상공인|사업화")

# ─────────────────────────────────────────────────────────────────── 비개인 키워드 블랙리스트

NON_INDIVIDUAL_RE = re.compile(
    r"(법\s*인\s*(사업자\s*)?(전용|대상|지원금|융자)"
    r"|기\s*업\s*(전용|대상\s*지원금|융자)"
    r"|시\s*설\s*(전용|대상|지원)"
    r"|단\s*체\s*(전용|대상|지원금)"
    r")"
)

# ─────────────────────────────────────────────────────────────────── 유틸

def clean(v):
    if v is None:
        return None
    text = str(v).strip()
    return text or None


def one_line(v):
    text = clean(v)
    return re.sub(r"\s+", " ", text) if text else None


def cut(v, limit):
    if v is None:
        return None
    return v if len(v) <= limit else v[: limit - 1] + "…"


def sql_str(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("'", "''").replace("${", "$ {") + "'"


def sql_int(v):
    return "NULL" if v is None else str(int(v))


def sql_date(v):
    return "NULL" if v is None else f"DATE '{v.isoformat()}'"


def sql_ts(v):
    return "NULL" if v is None else f"TIMESTAMP '{v.strftime('%Y-%m-%d %H:%M:%S')}'"


def sql_jsonb(v):
    if v in (None, [], {}):
        return "NULL"
    return sql_str(json.dumps(v, ensure_ascii=False)) + "::jsonb"


# ─────────────────────────────────────────────────────────────────── 카테고리 추론

def categories_of(title: str | None, summary: str | None) -> list[str]:
    text = f"{title or ''} {summary or ''}"
    for pattern, cats in CATEGORY_RE:
        if pattern.search(text):
            result = list(cats)
            # 창업 관련 키워드가 있으면 STARTUP 추가
            if STARTUP_RE.search(text) and "STARTUP" not in result:
                result.append("STARTUP")
            return result
    # 아무 규칙도 매칭 안 되면 WELFARE 기본값
    return ["WELFARE"]


# ─────────────────────────────────────────────────────────────────── 최종수정일 파싱

def parse_last_modified(v: str | None) -> datetime | None:
    if not v:
        return None
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", v.strip())
    if m:
        try:
            return datetime(int(m.group(1)), int(m.group(2)), int(m.group(3)))
        except ValueError:
            pass
    return None


# ─────────────────────────────────────────────────────────────────── 메인

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--today", default=date.today().isoformat())
    args = parser.parse_args()
    today = date.fromisoformat(args.today)
    verified_at = datetime.now()

    import sys
    if JSON_PATH.exists():
        body = __import__("json").loads(JSON_PATH.read_text(encoding="utf-8"))
        rows = body["data"]
        fetched_at = body.get("fetchedAt", "")
        print(f"[bokjiro-csv] JSON 소스 사용: {JSON_PATH.name} ({len(rows)}건, {fetched_at})")
    elif CSV_PATH.exists():
        import csv as _csv
        with CSV_PATH.open(encoding="utf-8-sig", newline="") as f:
            rows = list(_csv.DictReader(f))
        print(f"[bokjiro-csv] CSV 소스 사용: {CSV_PATH.name} ({len(rows)}건)")
    else:
        sys.exit(
            "[bokjiro-csv] raw/bokjiro_odcloud.json 또는 raw/bokjiro.csv 가 없습니다. "
            "먼저 fetch_bokjiro_csv.py 를 실행하거나 CSV 파일을 복사하세요."
        )

    stats: Counter = Counter()
    unknown_cats: Counter = Counter()
    org_types: dict = {}
    policies: list = []
    seen_ids: set = set()


    for row in rows:
        stats["전체"] += 1

        svc_id = one_line(row.get("서비스아이디"))
        if not svc_id:
            stats["제외: ID 없음"] += 1
            continue
        if svc_id in seen_ids:
            stats["제외: 중복 ID"] += 1
            continue
        seen_ids.add(svc_id)

        title = cut(one_line(row.get("서비스명")), 200)
        summary = cut(one_line(row.get("서비스요약")), 300)
        org_name = cut(one_line(row.get("소관부처명")), 200)
        source_url = cut(one_line(row.get("서비스URL")), 500)
        contact = cut(one_line(row.get("대표문의")), 200)
        site_info = one_line(row.get("사이트"))  # 사이트명+URL 혼합, 메모용
        last_modified_raw = one_line(row.get("최종수정일"))
        source_updated_at = parse_last_modified(last_modified_raw)

        if not title or not org_name:
            stats["제외: 필수값 누락(서비스명·소관부처명)"] += 1
            continue

        # 비개인 키워드 블랙리스트
        combined = f"{title} {summary or ''}"
        if NON_INDIVIDUAL_RE.search(combined):
            stats["제외: 비개인 키워드(군인·선원·법인 등)"] += 1
            continue

        # 카테고리 추론
        categories = categories_of(title, summary)

        # 신청 정보 (URL)
        apply_url = source_url  # 서비스URL이 곧 신청 페이지

        # 기관 등록용
        org_types.setdefault(org_name, None)

        # 신청기간: CSV에 없음 → ALWAYS
        app_type = "ALWAYS"
        pol_status = "ALWAYS_OPEN"

        policies.append({
            "service_id": svc_id,
            "org_name": org_name,
            "title": title,
            "summary": summary,
            "description": None,      # CSV에 상세 설명 없음
            "target": None,           # CSV에 대상자 필드 없음
            "benefit": None,
            "method": None,           # 신청 방법 정보 없음
            "app_type": app_type,
            "start": None,
            "end": None,
            "scope": "NATIONAL",
            "regions": [],
            "status": pol_status,
            "source_url": source_url,
            "source_updated_at": source_updated_at,
            "categories": categories,
            "elig": {
                "min_age": None, "max_age": None, "gender": None,
                "income_type": None, "min_income": None, "max_income": None,
                "employment": None, "household": None,
                "additional": {},
            },
            "app_info": {
                "procedure": None,
                "documents": None,
                "url": apply_url,
                "contact": contact,
                "notes": cut(site_info, 300) if site_info else None,
            },
        })
        stats["포함: NATIONAL"] += 1

    write_sql(policies, org_types, verified_at, today)
    write_report(stats, policies, unknown_cats)
    print(f"[bokjiro-csv] 정책 {len(policies)}건 → {OUT_SQL.relative_to(SERVER_ROOT)}")
    print(f"[bokjiro-csv] 통계 → {REPORT.relative_to(SERVER_ROOT)}")


def write_sql(policies, org_types, verified_at, today):
    lines = [
        "-- 자동 생성 파일: scripts/policy-seed/transform_bokjiro_csv_to_sql.py 로 재생성하세요. 직접 수정 금지.",
        f"-- 출처: 한국사회보장정보원_복지서비스정보 CSV (bokjiro.go.kr), 상태 기준일 {today}",
        f"-- 정책 {len(policies)}건 (전국, 중앙부처 전용). local 프로필 전용 (classpath:db/localdata).",
        "",
        "-- 재실행 대비: 이전에 넣은 bokjiro CSV 출처 정책 제거",
        f"DELETE FROM policies WHERE source_url LIKE {sql_str(SOURCE_URL_LIKE)};",
        f"DELETE FROM policies WHERE source_url LIKE {sql_str(SOURCE_URL_ALT_LIKE)};",
        "",
        "INSERT INTO organizations (name, type)",
        "SELECT v.name, v.type FROM (VALUES",
        ",\n".join(f"    ({sql_str(n)}, NULL)" for n in sorted(org_types)),
        ") AS v(name, type)",
        "WHERE NOT EXISTS (SELECT 1 FROM organizations o WHERE o.name = v.name);",
        "",
    ]

    for p in policies:
        e, a = p["elig"], p["app_info"]
        cat_list = ", ".join(sql_str(c) for c in p["categories"])
        stmt = [
            f"-- {p['service_id']} {p['title']}",
            "WITH p AS (",
            "    INSERT INTO policies (organization_id, title, summary, description, target_description,",
            "        benefit_description, application_method, application_type, application_start_date,",
            "        application_end_date, region_scope, status, source_url, source_updated_at, last_verified_at)",
            f"    VALUES ((SELECT id FROM organizations WHERE name = {sql_str(p['org_name'])} ORDER BY id LIMIT 1),",
            f"        {sql_str(p['title'])}, {sql_str(p['summary'])}, {sql_str(p['description'])},",
            f"        {sql_str(p['target'])}, {sql_str(p['benefit'])}, {sql_str(p['method'])},",
            f"        {sql_str(p['app_type'])}, {sql_date(p['start'])}, {sql_date(p['end'])},",
            f"        {sql_str(p['scope'])}, {sql_str(p['status'])}, {sql_str(p['source_url'])},",
            f"        {sql_ts(p['source_updated_at'])}, {sql_ts(verified_at)})",
            "    RETURNING id",
            "), c AS (",
            f"    INSERT INTO policy_categories (policy_id, category_id)",
            f"    SELECT p.id, c.id FROM p, categories c WHERE c.code IN ({cat_list})",
            "), e AS (",
            "    INSERT INTO policy_eligibility (policy_id, minimum_age, maximum_age, gender_condition, income_type,",
            "        minimum_income_value, maximum_income_value, allowed_employment_statuses,",
            "        allowed_household_types, additional_conditions)",
            f"    SELECT p.id, {sql_int(e['min_age'])}, {sql_int(e['max_age'])}, {sql_str(e['gender'])},",
            f"        {sql_str(e['income_type'])}, {sql_int(e['min_income'])}, {sql_int(e['max_income'])},",
            f"        {sql_jsonb(e['employment'])}, {sql_jsonb(e['household'])}, {sql_jsonb(e['additional'])} FROM p",
            "), a AS (",
            "    INSERT INTO policy_application_info (policy_id, application_procedure, required_documents_text,",
            "        application_url, contact_info, application_notes, source_url, verified_at)",
            f"    SELECT p.id, {sql_str(a['procedure'])}, {sql_str(a['documents'])}, {sql_str(a['url'])},",
            f"        {sql_str(a['contact'])}, {sql_str(a['notes'])}, {sql_str(p['source_url'])}, {sql_ts(verified_at)} FROM p",
            ")",
            "SELECT 1 FROM p;",  # NATIONAL이므로 policy_regions 삽입 없음
        ]
        lines.extend(stmt)
        lines.append("")

    OUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    OUT_SQL.write_text("\n".join(lines), encoding="utf-8")


def write_report(stats, policies, unknown_cats):
    from collections import Counter as C
    cat_count = C(c for p in policies for c in p["categories"])
    out = ["== 필터 통계"] + [f"  {k}: {v}" for k, v in stats.most_common()]
    out += ["", "== 카테고리 분포"] + [f"  {k}: {v}" for k, v in cat_count.most_common()]
    out += ["", "== 기관별 정책 수 (상위 20)"]
    org_count = C(p["org_name"] for p in policies)
    out += [f"  {k}: {v}" for k, v in org_count.most_common(20)]
    out += ["", "== 신청유형/상태"] + [f"  {k}: {v}" for k, v in C((p["app_type"], p["status"]) for p in policies).most_common()]
    REPORT.write_text("\n".join(out), encoding="utf-8")


if __name__ == "__main__":
    main()
