#!/usr/bin/env python3
"""복지로 복지서비스 목록정보 API 전량 수집 스크립트.

표준 라이브러리만 사용하므로 별도 설치 없이 macOS 기본 python3로 실행된다.

사용법:
    export BOKJIRO_API_KEY='공공데이터포털 일반 인증키(Decoding)'
    python3 scripts/policy-seed/fetch_bokjiro.py

결과: scripts/policy-seed/raw/bokjiro_list.json
(raw/는 .gitignore 처리되어 커밋되지 않는다)

API 출처:
    한국사회보장정보원_복지서비스목록정보 (data.go.kr, 공급자코드 B460011)
    https://www.data.go.kr/data/15002609/openapi.do

활용신청 키워드: "복지로 복지서비스목록" 또는 "B460011"
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

# data.go.kr B460011 복지서비스목록정보
BASE_URL = "https://apis.data.go.kr/B460011/welfareInfoService/getWlfareInfo"
PER_PAGE = 1000
MAX_PAGES = 50
RAW_DIR = Path(__file__).resolve().parent / "raw"

# callTp: P=개인 대상, H=가구 대상 (법인·단체는 의도적으로 제외)
CALL_TYPES = ["P", "H"]


def fetch_page(call_tp: str, page: int, api_key: str) -> dict:
    """복지로 API 한 페이지 조회."""
    params = {
        "serviceKey": api_key,
        "callTp": call_tp,
        "pageNo": str(page),
        "numOfRows": str(PER_PAGE),
        "_type": "json",
    }
    url = f"{BASE_URL}?{urllib.parse.urlencode(params)}"
    request = urllib.request.Request(url, headers={"User-Agent": "mozip-policy-seed/1.0"})
    last_error = None
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(request, timeout=60) as resp:
                body = json.loads(resp.read().decode("utf-8"))
            # 정상 응답 확인
            result_code = (
                body.get("response", {})
                .get("header", {})
                .get("resultCode", "")
            )
            if result_code not in ("00", "0"):
                msg = body.get("response", {}).get("header", {}).get("resultMsg", "")
                if result_code in ("30", "31", "32"):  # 인증 오류
                    sys.exit(f"[bokjiro] 인증 실패(code={result_code}): {msg}")
                raise ValueError(f"API 오류 code={result_code}: {msg}")
            return body
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")[:300]
            if exc.code in (401, 403):
                sys.exit(f"[bokjiro] 인증 실패(HTTP {exc.code}). 키 승인 직후라면 1~2시간 뒤 다시 시도하세요.\n{detail}")
            last_error = f"HTTP {exc.code} {detail}"
        except Exception as exc:
            last_error = repr(exc)
        wait = attempt * 3
        print(f"[bokjiro] callTp={call_tp} p{page} 실패({attempt}/5), {wait}s 후 재시도: {last_error}")
        time.sleep(wait)
    sys.exit(f"[bokjiro] callTp={call_tp} p{page} 최종 실패: {last_error}")


def extract_rows(body: dict) -> tuple[list, int]:
    """응답 body에서 items 목록과 totalCount 추출."""
    b = body.get("response", {}).get("body", {})
    total = int(b.get("totalCount", 0))
    items = b.get("items", {})
    if not items:
        return [], total
    row = items.get("item", [])
    # 단건 응답은 dict로 오는 경우가 있음
    if isinstance(row, dict):
        row = [row]
    return row, total


def fetch_all(call_tp: str, api_key: str) -> list:
    rows, total = [], None
    for page in range(1, MAX_PAGES + 1):
        body = fetch_page(call_tp, page, api_key)
        batch, fetched_total = extract_rows(body)
        if total is None:
            total = fetched_total
        rows.extend(batch)
        print(f"[bokjiro] callTp={call_tp} p{page}: 누적 {len(rows)}/{total}")
        if not batch or len(rows) >= total:
            break
        time.sleep(0.3)
    return rows


def main() -> None:
    api_key = os.environ.get("BOKJIRO_API_KEY", "").strip()
    if not api_key:
        sys.exit(
            "[bokjiro] BOKJIRO_API_KEY 환경변수를 설정하세요.\n"
            "  공공데이터포털(data.go.kr)에서 '한국사회보장정보원_복지서비스목록정보' 활용 신청 후\n"
            "  일반 인증키(Decoding) 값을 사용합니다."
        )
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    fetched_at = datetime.now(timezone.utc).isoformat()

    all_rows: list = []
    seen_ids: set = set()
    for call_tp in CALL_TYPES:
        rows = fetch_all(call_tp, api_key)
        added = 0
        for row in rows:
            sid = str(row.get("servId", "")).strip()
            if sid and sid not in seen_ids:
                seen_ids.add(sid)
                row["_callTp"] = call_tp  # 어느 유형으로 수집됐는지 메타 보존
                all_rows.append(row)
                added += 1
        print(f"[bokjiro] callTp={call_tp}: {len(rows)}건 수집, 중복 제외 후 {added}건 추가")

    out = RAW_DIR / "bokjiro_list.json"
    out.write_text(
        json.dumps({"fetchedAt": fetched_at, "data": all_rows}, ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"[bokjiro] 저장: {out} (총 {len(all_rows)}건, 중복 제거 완료)")
    print("[bokjiro] 완료 — 다음으로 transform_bokjiro_to_sql.py 를 실행하세요.")


if __name__ == "__main__":
    main()
