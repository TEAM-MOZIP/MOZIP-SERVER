#!/usr/bin/env python3
"""raw/*.json(정부24 공공서비스 API 원본) → MOZIP 스키마용 Flyway SQL 변환.

사용법:
    python3 scripts/policy-seed/transform_to_sql.py [--today YYYY-MM-DD]

출력:
    src/main/resources/db/localdata/R__seed_gov24_policies.sql   (local 프로필에서만 로드)
    scripts/policy-seed/raw/report.txt                           (매핑 통계·검수용 목록)

설계 메모
- 서비스 범위(V10)에 맞춰 전국(중앙행정기관·공공기관) + 서울특별시/서울 자치구 정책만 남긴다.
- 개인·가구·소상공인 대상이 아닌(법인/시설/단체 전용) 서비스, 농림축산어업 분야는 제외한다.
- supportConditions의 대상 플래그(JA03xx, JA04xx)는 "해당 대상을 배제하지 않음"에 가깝기 때문에
  allowed_* 목록은 판별력이 확실할 때만 채우고, 나머지는 additional_conditions.targetTags로 보존한다.
- Repeatable migration(R__)이라 이 파일 내용이 바뀌면 local DB에서 다시 실행된다.
  재실행 시 gov.kr 출처 정책을 먼저 지우고 다시 넣는다(해당 정책의 local 북마크·알림도 CASCADE 삭제).
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
OUT_SQL = SERVER_ROOT / "src/main/resources/db/localdata/R__seed_gov24_policies.sql"
REPORT = RAW_DIR / "report.txt"
SOURCE_URL_PREFIX = "https://www.gov.kr/portal/rcvfvrSvc/"

# ---------------------------------------------------------------- 매핑 테이블

SEOUL_DISTRICTS = {
    "종로구": "SEOUL_JONGNO", "중구": "SEOUL_JUNG", "용산구": "SEOUL_YONGSAN", "성동구": "SEOUL_SEONGDONG",
    "광진구": "SEOUL_GWANGJIN", "동대문구": "SEOUL_DONGDAEMUN", "중랑구": "SEOUL_JUNGNANG",
    "성북구": "SEOUL_SEONGBUK", "강북구": "SEOUL_GANGBUK", "도봉구": "SEOUL_DOBONG", "노원구": "SEOUL_NOWON",
    "은평구": "SEOUL_EUNPYEONG", "서대문구": "SEOUL_SEODAEMUN", "마포구": "SEOUL_MAPO",
    "양천구": "SEOUL_YANGCHEON", "강서구": "SEOUL_GANGSEO", "구로구": "SEOUL_GURO", "금천구": "SEOUL_GEUMCHEON",
    "영등포구": "SEOUL_YEONGDEUNGPO", "동작구": "SEOUL_DONGJAK", "관악구": "SEOUL_GWANAK", "서초구": "SEOUL_SEOCHO",
    "강남구": "SEOUL_GANGNAM", "송파구": "SEOUL_SONGPA", "강동구": "SEOUL_GANGDONG",
}

# 서비스분야 → categories.code (기존 6개 코드만 사용)
FIELD_TO_CATEGORIES = {
    "주거·자립": ["HOUSING"],
    "보육·교육": ["EDUCATION"],
    "고용·창업": ["EMPLOYMENT"],
    "생활안정": ["WELFARE"],
    "보건·의료": ["WELFARE"],
    "임신·출산": ["WELFARE"],
    "보호·돌봄": ["WELFARE"],
    "행정·안전": ["WELFARE"],
    "문화·환경": ["CULTURE"],
}
EXCLUDED_FIELDS = {"농림축산어업"}
STARTUP_WORDS = re.compile(r"창업|스타트업|벤처|소상공인|사업화")

# 중위소득 구간 플래그 → (하한, 상한). 상한 None = 제한 없음
INCOME_BRACKETS = [("JA0201", 0, 50), ("JA0202", 51, 75), ("JA0203", 76, 100), ("JA0204", 101, 200), ("JA0205", 201, None)]

# 대상 플래그 → 사람이 읽는 라벨(additional_conditions.targetTags용). 코드 정의는 공식 Swagger 기준으로 확인함
# https://infuser.odcloud.kr/api/stages/44436/api-docs
TARGET_LABELS = {
    "JA0301": "예비부모/난임", "JA0302": "임산부", "JA0303": "출산/입양",
    "JA0313": "농업인", "JA0314": "어업인", "JA0315": "축산업인", "JA0316": "임업인",
    "JA0317": "초등학생", "JA0318": "중학생", "JA0319": "고등학생", "JA0320": "대학생/대학원생",
    "JA0326": "근로자/직장인", "JA0327": "구직자/실업자", "JA0328": "장애인", "JA0329": "국가보훈대상자",
    "JA0330": "질병/질환자",
    "JA0401": "다문화가족", "JA0402": "북한이탈주민", "JA0403": "한부모가정/조손가정", "JA0404": "1인가구",
    "JA0411": "다자녀가구", "JA0412": "무주택세대", "JA0413": "신규전입", "JA0414": "확대가족",
    "JA1101": "예비창업자", "JA1102": "영업중", "JA1103": "생계곤란/폐업예정자",
}
PERSONAL_NONE = "JA0322"   # 개인 대상 특성: 해당사항없음(= 누구나)
HOUSEHOLD_NONE = "JA0410"  # 가구 대상 특성: 해당사항없음(= 누구나)
EMPLOYED, JOB_SEEKER = "JA0326", "JA0327"
HOUSEHOLD_FLAG_TO_ENUM = {"JA0404": "SINGLE", "JA0403": "SINGLE_PARENT"}
# JA0328(장애인)은 개인 대상 플래그로 "배제하지 않음" 의미라 DISABLED 가구유형으로 쓰지 않는다(targetTags로만 보존)
HOUSEHOLD_FLAGS = ["JA0401", "JA0402", "JA0403", "JA0404", "JA0411", "JA0412", "JA0413", "JA0414"]

# ---------------------------------------------------------------- 유틸


def clean(value):
    if value is None:
        return None
    text = str(value).replace("\r\n", "\n").strip()
    return text or None


def one_line(value):
    text = clean(value)
    return re.sub(r"\s+", " ", text) if text else None


def cut(value, limit):
    if value is None:
        return None
    return value if len(value) <= limit else value[: limit - 1] + "…"


def sql_str(value):
    if value is None:
        return "NULL"
    # Flyway 플레이스홀더(${...})로 오인되지 않게 분리
    return "'" + str(value).replace("'", "''").replace("${", "$ {") + "'"


def sql_int(value):
    return "NULL" if value is None else str(int(value))


def sql_date(value):
    return "NULL" if value is None else f"DATE '{value.isoformat()}'"


def sql_ts(value):
    return "NULL" if value is None else f"TIMESTAMP '{value.strftime('%Y-%m-%d %H:%M:%S')}'"


def sql_jsonb(value):
    if value in (None, [], {}):
        return "NULL"
    return sql_str(json.dumps(value, ensure_ascii=False)) + "::jsonb"


def to_int(value):
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return None


def parse_ts(value):
    text = clean(value)
    if not text:
        return None
    digits = re.sub(r"\D", "", text)
    for fmt, size in (("%Y%m%d%H%M%S", 14), ("%Y%m%d", 8)):
        if len(digits) >= size:
            try:
                return datetime.strptime(digits[:size], fmt)
            except ValueError:
                pass
    return None


ALWAYS_RE = re.compile(r"^(상시|연중|수시)|신청\s*(이\s*)?불필요|신청\s*절차\s*(가\s*)?없|별도\s*(의\s*)?신청\s*(기한|기간|절차)\s*(이\s*|가\s*)?없")
DATE_RE = re.compile(r"(20\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})")


def parse_period(deadline):
    """신청기한 문자열 → (application_type, start, end)."""
    text = one_line(deadline)
    if not text:
        return "UNKNOWN", None, None
    if ALWAYS_RE.search(text):
        return "ALWAYS", None, None
    dates = []
    for y, m, d in DATE_RE.findall(text):
        try:
            dates.append(date(int(y), int(m), int(d)))
        except ValueError:
            pass
    if len(dates) >= 2 and dates[0] <= dates[1]:
        return "PERIOD", dates[0], dates[1]
    return "UNKNOWN", None, None


def split_multi(value):
    return [part.strip() for part in (clean(value) or "").split("||") if part.strip()]


def pick(row, *keys):
    for key in keys:
        if row and clean(row.get(key)):
            return clean(row.get(key))
    return None


# ---------------------------------------------------------------- 판정 로직


def region_of(row):
    """→ ('NATIONAL', []) | ('REGIONAL', [region codes]) | None(범위 밖)"""
    org_type = one_line(row.get("소관기관유형")) or ""
    org_name = re.sub(r"^\((재|사)\)\s*", "", one_line(row.get("소관기관명")) or "")
    if org_type in ("중앙행정기관", "공공기관"):
        return "NATIONAL", []
    if not org_name.startswith("서울"):
        return None
    rest = re.sub(r"^서울(특별시|시)?", "", org_name).strip()
    for district, code in SEOUL_DISTRICTS.items():
        if rest.startswith(district):
            return "REGIONAL", [code]
    return "REGIONAL", ["SEOUL"]


def organization_type(row):
    org_type = one_line(row.get("소관기관유형")) or ""
    return {"중앙행정기관": "중앙부처", "지방자치단체": "지자체"}.get(org_type, org_type or None)


def categories_of(row):
    field = one_line(row.get("서비스분야"))
    categories = list(FIELD_TO_CATEGORIES.get(field, []))
    text = f"{row.get('서비스명') or ''} {row.get('서비스목적요약') or ''}"
    if STARTUP_WORDS.search(text) and "STARTUP" not in categories:
        categories.append("STARTUP")
    return categories


def is_for_individuals(row):
    users = split_multi(row.get("사용자구분"))
    if not users:
        return True
    return any(u in ("개인", "가구", "소상공인") for u in users)


def eligibility_of(cond, selection_criteria):
    """supportConditions 한 행 → policy_eligibility 컬럼 dict"""
    result = {"min_age": None, "max_age": None, "gender": None, "income_type": None,
              "min_income": None, "max_income": None, "employment": None, "household": None,
              "additional": {}}
    if selection_criteria:
        result["additional"]["selectionCriteria"] = cut(selection_criteria, 1000)
    if not cond:
        return result
    yes = {k for k, v in cond.items() if str(v).strip().upper() == "Y"}

    min_age, max_age = to_int(cond.get("JA0110")), to_int(cond.get("JA0111"))
    if min_age is not None and min_age > 0:
        result["min_age"] = min_age
    if max_age is not None and max_age < 100:
        result["max_age"] = max_age
    if result["min_age"] and result["max_age"] and result["min_age"] > result["max_age"]:
        result["min_age"] = result["max_age"] = None

    male, female = "JA0101" in yes, "JA0102" in yes
    if male != female:
        result["gender"] = "MALE" if male else "FEMALE"

    brackets = [b for b in INCOME_BRACKETS if b[0] in yes]
    if brackets and len(brackets) < len(INCOME_BRACKETS):
        low, high = brackets[0][1], brackets[-1][2]
        result["income_type"] = "MEDIAN_PERCENTAGE"
        result["max_income"] = high  # 하한은 데이터 품질이 낮아 쓰지 않는다(저소득자 오탈락 방지)
        if high is None:
            result["income_type"] = None

    if PERSONAL_NONE not in yes:
        employed, seeker = EMPLOYED in yes, JOB_SEEKER in yes
        if employed != seeker:
            result["employment"] = ["EMPLOYED"] if employed else ["JOB_SEEKER", "UNEMPLOYED"]

    household_yes = [f for f in HOUSEHOLD_FLAGS if f in yes]
    if HOUSEHOLD_NONE not in yes and household_yes and all(f in HOUSEHOLD_FLAG_TO_ENUM for f in household_yes):
        result["household"] = sorted({HOUSEHOLD_FLAG_TO_ENUM[f] for f in household_yes})

    tags = [label for code, label in TARGET_LABELS.items() if code in yes]
    if tags:
        result["additional"]["targetTags"] = tags
    return result


def status_of(app_type, end, today):
    if app_type == "ALWAYS":
        return "ALWAYS_OPEN"
    if app_type == "PERIOD" and end < today:
        return "CLOSED"
    return "OPEN"


# ---------------------------------------------------------------- 메인


def load(name):
    body = json.loads((RAW_DIR / f"{name}.json").read_text(encoding="utf-8"))
    return body["data"], body.get("fetchedAt")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--today", default=date.today().isoformat())
    args = parser.parse_args()
    today = date.fromisoformat(args.today)

    services, fetched_at = load("serviceList")
    details, _ = load("serviceDetail")
    conditions, _ = load("supportConditions")
    detail_by_id = {clean(d.get("서비스ID")): d for d in details}
    cond_by_id = {clean(c.get("서비스ID")): c for c in conditions}
    verified_at = parse_ts(fetched_at) or datetime.now()

    stats = Counter()
    unknown_deadlines = Counter()
    unknown_fields = Counter()
    org_types = {}
    policies = []
    seen_ids = set()

    for row in services:
        stats["전체"] += 1
        service_id = clean(row.get("서비스ID"))
        if not service_id or service_id in seen_ids:
            stats["제외: ID 없음/중복"] += 1
            continue
        seen_ids.add(service_id)
        region = region_of(row)
        if region is None:
            stats["제외: 서울·전국 외 지역"] += 1
            continue
        field = one_line(row.get("서비스분야"))
        if field in EXCLUDED_FIELDS:
            stats["제외: 농림축산어업"] += 1
            continue
        if not is_for_individuals(row):
            stats["제외: 법인/시설/단체 전용"] += 1
            continue
        categories = categories_of(row)
        if not categories:
            unknown_fields[field] += 1
            stats["제외: 분야 매핑 없음"] += 1
            continue

        detail = detail_by_id.get(service_id, {})
        title = cut(one_line(pick(row, "서비스명") or pick(detail, "서비스명")), 200)
        org_name = cut(one_line(row.get("소관기관명")), 200)
        if not title or not org_name:
            stats["제외: 필수값 누락"] += 1
            continue

        deadline = pick(detail, "신청기한") or pick(row, "신청기한")
        app_type, start, end = parse_period(deadline)
        if app_type == "UNKNOWN" and deadline:
            unknown_deadlines[one_line(deadline)] += 1
        source_url = cut(pick(row, "상세조회URL") or f"{SOURCE_URL_PREFIX}dtlEx/{service_id}", 500)
        target = pick(detail, "지원대상") or pick(row, "지원대상")
        criteria = pick(detail, "선정기준") or pick(row, "선정기준")

        org_types.setdefault(org_name, organization_type(row))
        policies.append({
            "service_id": service_id,
            "org_name": org_name,
            "title": title,
            "summary": cut(one_line(pick(row, "서비스목적요약")), 300),
            "description": pick(detail, "서비스목적") or pick(row, "서비스목적요약"),
            "target": target,
            "benefit": pick(detail, "지원내용") or pick(row, "지원내용"),
            "method": pick(detail, "신청방법") or pick(row, "신청방법"),
            "app_type": app_type, "start": start, "end": end,
            "scope": region[0], "regions": region[1],
            "status": status_of(app_type, end, today),
            "source_url": source_url,
            "source_updated_at": parse_ts(pick(row, "수정일시")),
            "categories": categories,
            "elig": eligibility_of(cond_by_id.get(service_id), criteria),
            "app_info": {
                "procedure": pick(detail, "신청방법") or pick(row, "신청방법"),
                "documents": pick(detail, "구비서류"),
                "url": cut(one_line(pick(detail, "온라인신청사이트URL")), 500),
                "contact": pick(detail, "문의처") or pick(row, "전화문의"),
                "notes": "\n".join(filter(None, [
                    f"신청기한: {one_line(deadline)}" if deadline else None,
                    f"접수기관: {one_line(pick(detail, '접수기관명') or pick(row, '접수기관'))}"
                    if pick(detail, "접수기관명") or pick(row, "접수기관") else None,
                ])) or None,
            },
        })
        stats[f"포함: {region[0]}"] += 1

    write_sql(policies, org_types, verified_at, fetched_at, today)
    write_report(stats, policies, unknown_deadlines, unknown_fields)
    print(f"[transform] 정책 {len(policies)}건 → {OUT_SQL.relative_to(SERVER_ROOT)}")
    print(f"[transform] 통계·검수 목록 → {REPORT.relative_to(SERVER_ROOT)}")


def write_sql(policies, org_types, verified_at, fetched_at, today):
    lines = [
        "-- 자동 생성 파일: scripts/policy-seed/transform_to_sql.py 로 재생성하세요. 직접 수정 금지.",
        f"-- 출처: 행정안전부_대한민국 공공서비스(혜택) 정보 API (gov24/v3), 수집 시각 {fetched_at}, 상태 기준일 {today}",
        f"-- 정책 {len(policies)}건 (전국 + 서울). local 프로필 전용 (classpath:db/localdata).",
        "",
        "-- 재실행 대비: 이전에 넣은 gov.kr 출처 정책 제거 (하위 테이블은 ON DELETE CASCADE)",
        f"DELETE FROM policies WHERE source_url LIKE {sql_str(SOURCE_URL_PREFIX + '%')};",
        "",
        "INSERT INTO organizations (name, type)",
        "SELECT v.name, v.type FROM (VALUES",
        ",\n".join(f"    ({sql_str(n)}, {sql_str(t)})" for n, t in sorted(org_types.items())),
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


def write_report(stats, policies, unknown_deadlines, unknown_fields):
    out = ["== 필터 통계"] + [f"{k}: {v}" for k, v in stats.most_common()]
    out += ["", "== 카테고리"] + [f"{k}: {v}" for k, v in Counter(c for p in policies for c in p["categories"]).most_common()]
    out += ["", "== 신청유형/상태"] + [f"{k}: {v}" for k, v in Counter((p["app_type"], p["status"]) for p in policies).most_common()]
    out += ["", "== 서울 지역"] + [f"{k}: {v}" for k, v in Counter(r for p in policies for r in p["regions"]).most_common()]
    el = [p["elig"] for p in policies]
    out += ["", "== 자격조건 채움률",
            f"나이: {sum(1 for e in el if e['min_age'] or e['max_age'])}",
            f"성별: {sum(1 for e in el if e['gender'])}",
            f"소득: {sum(1 for e in el if e['income_type'])}",
            f"고용상태: {sum(1 for e in el if e['employment'])}",
            f"가구유형: {sum(1 for e in el if e['household'])}"]
    out += ["", "== 매핑 안 된 서비스분야"] + [f"{k}: {v}" for k, v in unknown_fields.most_common()]
    out += ["", "== 기간 파싱 실패(UNKNOWN 처리) 상위 50"] + [f"{v}건 | {k}" for k, v in unknown_deadlines.most_common(50)]
    REPORT.write_text("\n".join(out), encoding="utf-8")


if __name__ == "__main__":
    main()
