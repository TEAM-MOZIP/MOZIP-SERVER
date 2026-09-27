#!/usr/bin/env python3
"""raw/bokjiro_list.json(복지로 복지서비스목록 API 원본) → MOZIP 스키마용 Flyway SQL 변환.

사용법:
    python3 scripts/policy-seed/transform_bokjiro_to_sql.py [--today YYYY-MM-DD]

출력:
    src/main/resources/db/localdata/R__seed_bokjiro_policies.sql   (local 프로필에서만 로드)
    scripts/policy-seed/raw/bokjiro_report.txt                     (매핑 통계·검수용)

설계 메모
- callTp=P(개인)/H(가구)로 수집했으므로 법인·단체 서비스는 대부분 제외된 상태.
- 지역 필터: ctpvNm == "전체" → NATIONAL, ctpvNm == "서울" → REGIONAL.
  sggNm으로 서울 자치구 구체화.
- 비개인 키워드 블랙리스트: 군인·선원 등 특수직 대상 서비스 추가 제거.
- 상태코드: sttCd 00=운영중, 01=종료예정, 02=종료.
- Repeatable migration(R__)이라 SQL이 바뀌면 local DB에서 재실행된다.
  재실행 시 bokjiro 출처 정책을 먼저 지우고 다시 넣는다.
"""
import argparse
import json
import re
from collections import Counter
from datetime import date, datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW_DIR = HERE / "raw"
SERVER_ROOT = HERE.parent.parent
OUT_SQL = SERVER_ROOT / "src/main/resources/db/localdata/R__seed_bokjiro_policies.sql"
REPORT = RAW_DIR / "bokjiro_report.txt"
SOURCE_URL_PREFIX = "https://bokjiro.go.kr/ssis-tbu/twatbz/gvatnAltBz/dtlWlfareSvc.do?wlfareSvcId="

# ---------------------------------------------------------------- 서울 자치구

SEOUL_DISTRICTS = {
    "종로구": "SEOUL_JONGNO", "중구": "SEOUL_JUNG", "용산구": "SEOUL_YONGSAN", "성동구": "SEOUL_SEONGDONG",
    "광진구": "SEOUL_GWANGJIN", "동대문구": "SEOUL_DONGDAEMUN", "중랑구": "SEOUL_JUNGNANG",
    "성북구": "SEOUL_SEONGBUK", "강북구": "SEOUL_GANGBUK", "도봉구": "SEOUL_DOBONG", "노원구": "SEOUL_NOWON",
    "은평구": "SEOUL_EUNPYEONG", "서대문구": "SEOUL_SEODAEMUN", "마포구": "SEOUL_MAPO",
    "양천구": "SEOUL_YANGCHEON", "강서구": "SEOUL_GANGSEO", "구로구": "SEOUL_GURO", "금천구": "SEOUL_GEUMCHEON",
    "영등포구": "SEOUL_YEONGDEUNGPO", "동작구": "SEOUL_DONGJAK", "관악구": "SEOUL_GWANAK", "서초구": "SEOUL_SEOCHO",
    "강남구": "SEOUL_GANGNAM", "송파구": "SEOUL_SONGPA", "강동구": "SEOUL_GANGDONG",
}

# ---------------------------------------------------------------- 관심주제 → 카테고리

THEME_TO_CATEGORIES = {
    # 복지로 intrsThemaCd 기준 (API 응답의 intrsThemaNm 으로도 매핑)
    "001": ["WELFARE"],    # 생활안정
    "002": ["HOUSING"],    # 주거
    "003": ["EMPLOYMENT"], # 고용/창업
    "004": ["WELFARE"],    # 보건의료
    "005": ["WELFARE"],    # 행정/안전/법률
    "006": ["WELFARE"],    # 임신/출산
    "007": ["EDUCATION"],  # 보육/교육
    "008": ["WELFARE"],    # 노후
    "009": ["WELFARE"],    # 장애/보훈
    "010": ["CULTURE"],    # 문화/환경/여가
    "011": ["WELFARE"],    # 저소득/취약계층
    "012": ["WELFARE"],    # 다문화/탈북
}
THEME_NAME_TO_CATEGORIES = {
    "생활안정": ["WELFARE"], "주거": ["HOUSING"], "고용·창업": ["EMPLOYMENT"], "고용/창업": ["EMPLOYMENT"],
    "보건의료": ["WELFARE"], "행정·안전·법률": ["WELFARE"], "임신·출산": ["WELFARE"],
    "보육·교육": ["EDUCATION"], "노후": ["WELFARE"], "장애·보훈": ["WELFARE"],
    "문화·환경·여가": ["CULTURE"], "저소득·취약계층": ["WELFARE"], "다문화·탈북": ["WELFARE"],
}
STARTUP_RE = re.compile(r"창업|스타트업|벤처|소상공인|사업화")

# ---------------------------------------------------------------- 비개인 대상 키워드 블랙리스트

# 제목 또는 요약에 아래 패턴이 있으면 개인 대상이 아닌 것으로 판단해 제외한다.
# "군인 가족/자녀", "선원 가족" 같은 피부양자 혜택은 포함되어야 하므로
# 단독 주어로 쓰인 경우만 제외하도록 패턴을 신중하게 작성한다.
NON_INDIVIDUAL_RE = re.compile(
    r"(군\s*인\s*(대상|전용|지원|급여|수당|혜택|복지)"
    r"|현\s*역\s*병|부\s*사\s*관\s*(지원|대상|급여|혜택)"
    r"|군\s*무\s*원|장\s*교\s*(지원|혜택|대상)"
    r"|선\s*원\s*(대상|전용|지원|급여|등록)"
    r"|함\s*정\s*승\s*선"
    r"|영\s*농\s*조\s*합|어\s*촌\s*계"
    r"|법\s*인\s*(사업자|대상|전용|지원금)"
    r"|기\s*업\s*(전용|대상|지원금|융자)"
    r"|협\s*동\s*조\s*합\s*(지원|대상)"
    r")"
)

# ---------------------------------------------------------------- 지원 형태 → 생애주기 힌트 (age 추정에 활용 예정)
# 현재는 복지로 API가 나이 범위를 직접 제공하지 않아 비워 둠.
# lifeNmArray(생애주기) → 나이 추론은 향후 개선 예정.

# ---------------------------------------------------------------- 유틸

def clean(v):
    if v is None:
        return None
    text = str(v).replace("\r\n", "\n").strip()
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


ALWAYS_RE = re.compile(r"^(상시|연중|수시)|신청\s*(이\s*)?불필요|별도\s*(의\s*)?신청\s*없")
DATE_RE = re.compile(r"(20\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})")
DATE_COMPACT_RE = re.compile(r"(20\d{2})(\d{2})(\d{2})")


def parse_date(text: str | None) -> date | None:
    if not text:
        return None
    # YYYYMMDD 형식 (복지로 API는 주로 이 형식)
    m = DATE_COMPACT_RE.fullmatch(text.strip())
    if m:
        try:
            return date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
        except ValueError:
            pass
    # YYYY.MM.DD 등
    for y, mon, d in DATE_RE.findall(text):
        try:
            return date(int(y), int(mon), int(d))
        except ValueError:
            pass
    return None


def parse_period(row: dict):
    """aplyBgngDt / aplyEndDt → (application_type, start, end)."""
    bgng = one_line(row.get("aplyBgngDt"))
    end_s = one_line(row.get("aplyEndDt"))
    start = parse_date(bgng)
    end = parse_date(end_s)
    if start and end and start <= end:
        return "PERIOD", start, end
    # 공란이면 ALWAYS 로 처리(복지로는 상시 서비스가 많음)
    return "ALWAYS", None, None


def status_of(stt_cd: str, app_type: str, end, today: date) -> str:
    if stt_cd == "02":  # 종료
        return "CLOSED"
    if app_type == "ALWAYS":
        return "ALWAYS_OPEN"
    if app_type == "PERIOD" and end and end < today:
        return "CLOSED"
    return "OPEN"


# ---------------------------------------------------------------- 지역 판정

def region_of(row: dict):
    """→ ('NATIONAL', []) | ('REGIONAL', [codes]) | None(제외)"""
    ctpv = one_line(row.get("ctpvNm")) or ""
    sgg = one_line(row.get("sggNm")) or ""
    if not ctpv or ctpv in ("전체", "전국", ""):
        return "NATIONAL", []
    if ctpv == "서울" or ctpv.startswith("서울"):
        for district, code in SEOUL_DISTRICTS.items():
            if district in sgg:
                return "REGIONAL", [code]
        return "REGIONAL", ["SEOUL"]
    # 그 외 지역은 제외
    return None


# ---------------------------------------------------------------- 카테고리 매핑

def categories_of(row: dict) -> list[str]:
    theme_cd = one_line(row.get("intrsThemaCd")) or ""
    theme_nm = one_line(row.get("intrsThemaNm")) or ""
    cats = (
        THEME_TO_CATEGORIES.get(theme_cd)
        or THEME_NAME_TO_CATEGORIES.get(theme_nm)
        or []
    )
    cats = list(cats)
    text = f"{row.get('servNm') or ''} {row.get('servDgst') or ''}"
    if STARTUP_RE.search(text) and "STARTUP" not in cats:
        cats.append("STARTUP")
    return cats


# ---------------------------------------------------------------- 개인 대상 판정

def is_for_individuals(row: dict, title: str | None, summary: str | None) -> bool:
    """callTp=P/H로 수집했지만 키워드 블랙리스트로 추가 검증."""
    combined = f"{title or ''} {summary or ''}"
    if NON_INDIVIDUAL_RE.search(combined):
        return False
    return True


# ---------------------------------------------------------------- 자격조건 (복지로는 상세 구조화 미제공 → additional 보존)

def eligibility_of(row: dict) -> dict:
    """복지로 API는 나이·소득 수치를 별도 필드로 제공하지 않음.
    대상자 정보를 additional_conditions 에 보존한다."""
    additional: dict = {}
    trgter = one_line(row.get("trgterNm")) or one_line(row.get("trgterIndvdlNm"))
    if trgter:
        additional["targetDescription"] = cut(trgter, 500)
    life = one_line(row.get("lifeNmArray"))
    if life:
        additional["lifeStage"] = cut(life, 200)
    return {
        "min_age": None, "max_age": None, "gender": None,
        "income_type": None, "min_income": None, "max_income": None,
        "employment": None, "household": None,
        "additional": additional if additional else {},
    }


# ---------------------------------------------------------------- 메인

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--today", default=date.today().isoformat())
    args = parser.parse_args()
    today = date.fromisoformat(args.today)

    raw_path = RAW_DIR / "bokjiro_list.json"
    if not raw_path.exists():
        import sys
        sys.exit(
            f"[bokjiro-transform] {raw_path} 이 없습니다. "
            "먼저 fetch_bokjiro.py 를 실행하세요."
        )
    body = json.loads(raw_path.read_text(encoding="utf-8"))
    services: list = body["data"]
    fetched_at: str = body.get("fetchedAt", "")
    verified_at = datetime.fromisoformat(fetched_at.replace("Z", "+00:00")) if fetched_at else datetime.now()

    stats: Counter = Counter()
    unknown_themes: Counter = Counter()
    org_types: dict = {}
    policies: list = []
    seen_ids: set = set()

    for row in services:
        stats["전체"] += 1
        svc_id = one_line(row.get("servId"))
        if not svc_id or svc_id in seen_ids:
            stats["제외: ID 없음/중복"] += 1
            continue
        seen_ids.add(svc_id)

        # 상태 확인 (sttCd 02=종료 → 포함하되 CLOSED 상태로)
        stt_cd = one_line(row.get("sttCd")) or "00"

        # 지역 필터
        region = region_of(row)
        if region is None:
            stats["제외: 서울·전국 외 지역"] += 1
            continue

        title = cut(one_line(row.get("servNm")), 200)
        summary = cut(one_line(row.get("servDgst")), 300)
        org_name = cut(one_line(row.get("rprsOrgnNm")), 200)
        if not title or not org_name:
            stats["제외: 필수값 누락"] += 1
            continue

        # 비개인 키워드 블랙리스트
        if not is_for_individuals(row, title, summary):
            stats["제외: 비개인 키워드"] += 1
            continue

        # 카테고리 매핑
        categories = categories_of(row)
        if not categories:
            unknown_themes[f"{row.get('intrsThemaCd')}:{row.get('intrsThemaNm')}"] += 1
            stats["제외: 분야 매핑 없음"] += 1
            continue

        # 기간
        app_type, start, end = parse_period(row)
        pol_status = status_of(stt_cd, app_type, end, today)
        source_url = cut(f"{SOURCE_URL_PREFIX}{svc_id}", 500)

        # 신청 정보
        method = one_line(row.get("aplyMthdNm"))
        contact = one_line(row.get("inqNum"))
        apply_url = cut(one_line(row.get("servUrl")), 500)

        org_types.setdefault(org_name, None)  # 복지로는 기관유형 미제공

        elig = eligibility_of(row)

        policies.append({
            "service_id": svc_id,
            "org_name": org_name,
            "title": title,
            "summary": summary,
            "description": cut(one_line(row.get("servCont")), 5000),
            "target": cut(one_line(row.get("trgterNm")), 1000),
            "benefit": None,  # 복지로 목록 API에는 별도 지원내용 필드 없음
            "method": method,
            "app_type": app_type, "start": start, "end": end,
            "scope": region[0], "regions": region[1],
            "status": pol_status,
            "source_url": source_url,
            "source_updated_at": None,
            "categories": categories,
            "elig": elig,
            "app_info": {
                "procedure": method,
                "documents": None,
                "url": apply_url,
                "contact": contact,
                "notes": None,
            },
        })
        stats[f"포함: {region[0]}"] += 1

    write_sql(policies, org_types, verified_at, fetched_at, today)
    write_report(stats, policies, unknown_themes)
    print(f"[bokjiro-transform] 정책 {len(policies)}건 → {OUT_SQL.relative_to(SERVER_ROOT)}")
    print(f"[bokjiro-transform] 통계·검수 목록 → {REPORT.relative_to(SERVER_ROOT)}")


def write_sql(policies, org_types, verified_at, fetched_at, today):
    lines = [
        "-- 자동 생성 파일: scripts/policy-seed/transform_bokjiro_to_sql.py 로 재생성하세요. 직접 수정 금지.",
        f"-- 출처: 한국사회보장정보원_복지서비스목록정보 API (복지로), 수집 시각 {fetched_at}, 상태 기준일 {today}",
        f"-- 정책 {len(policies)}건 (전국 + 서울). local 프로필 전용 (classpath:db/localdata).",
        "",
        "-- 재실행 대비: 이전에 넣은 bokjiro 출처 정책 제거",
        f"DELETE FROM policies WHERE source_url LIKE {sql_str(SOURCE_URL_PREFIX + '%')};",
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
        ]
        if p["regions"]:
            codes = ", ".join(sql_str(r) for r in p["regions"])
            stmt.append(f"INSERT INTO policy_regions (policy_id, region_id) SELECT p.id, r.id FROM p, regions r WHERE r.code IN ({codes});")
        else:
            stmt.append("SELECT 1 FROM p;")
        lines.extend(stmt)
        lines.append("")
    OUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    OUT_SQL.write_text("\n".join(lines), encoding="utf-8")


def write_report(stats, policies, unknown_themes):
    from collections import Counter as C
    out = ["== 필터 통계"] + [f"{k}: {v}" for k, v in stats.most_common()]
    out += ["", "== 카테고리"] + [f"{k}: {v}" for k, v in C(c for p in policies for c in p["categories"]).most_common()]
    out += ["", "== 지역 분포"] + [f"{k}: {v}" for k, v in C(r for p in policies for r in p["regions"]).most_common()]
    out += ["", "== 신청유형/상태"] + [f"{k}: {v}" for k, v in C((p["app_type"], p["status"]) for p in policies).most_common()]
    el = [p["elig"] for p in policies]
    out += ["", "== 자격조건 채움률",
            f"  나이: {sum(1 for e in el if e['min_age'] or e['max_age'])} (복지로 API는 나이 필드 미제공)",
            f"  소득: {sum(1 for e in el if e['income_type'])} (복지로 API는 소득 필드 미제공)",
            f"  targetDescription(additional): {sum(1 for e in el if e['additional'].get('targetDescription'))}"]
    out += ["", "== 매핑 안 된 intrsThemaCd:intrsThemaNm"] + [f"{k}: {v}" for k, v in unknown_themes.most_common()]
    REPORT.write_text("\n".join(out), encoding="utf-8")


if __name__ == "__main__":
    main()
